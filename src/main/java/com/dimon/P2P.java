package com.dimon;

import com.dimon.dto.Data;
import com.dimon.dto.DataFile;
import com.dimon.dto.ObjectToSend;
import com.dimon.managers.*;
import com.dimon.managers.FileDescriptor;
import com.dimon.protocol.MyUDPProtocol;
import com.dimon.protocol.ProtocolForCRC;
import com.dimon.protocol.ProtocolForCRCWithoutData;
import com.dimon.protocol.TypeOfMessage;
import com.dimon.utils.Constants;
import com.dimon.utils.Utils;

import java.io.*;
import java.net.DatagramPacket;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.dimon.utils.CRC.calculateCRC;
import static com.dimon.utils.CRC.isValidCRC;
import static com.dimon.protocol.TypeOfMessage.*;

/**
 * The main class for the Peer-to-Peer (P2P) application.
 * Handles file and message transfer between two peers over UDP using a custom protocol.
 */
public class P2P {
    public static volatile long lastKeepAliveTime = System.currentTimeMillis();
    public static volatile long lastActivityTime = System.currentTimeMillis();
    public static volatile int heartbeatFailures = 0;

    private static NetworkSetting networkSetting = new NetworkSetting();
    private static FragmentManager fragmentManager = new FragmentManager();
    private static FileManager fileManager = new FileManager();


    private static Queue<ObjectToSend> bufferOfInput = new ConcurrentLinkedQueue<>();
    private static AtomicReference<String> message = new AtomicReference<>(null);
    private static AtomicInteger lastProcessedSeqNumber = new AtomicInteger(-1);

    private static int receiver = 0;
    private static int sender = 0;

    /**
     * The main entry point for the P2P application.
     * Initializes network settings, starts threads for input handling, and processes incoming messages.
     *
     * @param args Command-line arguments (not used).
     */
    public static void main(String[] args) throws Exception{
        com.dimon.managers.FileDescriptor fileDescriptor = new com.dimon.managers.FileDescriptor();
        MessageDescriptor messageDescriptor = new MessageDescriptor();

        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter the port number to listen on:");
        int localPort;
        while (true) {
            try {
                localPort = Integer.parseInt(scanner.next());
                if (localPort > 0 && localPort <= 65535) {
                    break;
                } else {
                    System.out.println("Invalid port number. Please enter a number between 1 and 65535:");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid integer for the port number:");
            }
        }
        networkSetting.setLocalPort(localPort);


        System.out.println("Enter the peer's IP address:");
        String peerIp;
        while (true) {
            peerIp = scanner.next();
            if (peerIp.matches(
                    "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
                break;
            } else {
                System.out.println("Invalid IP address. Please enter a valid IPv4 address:");
            }
        }
        networkSetting.setPeerIp(peerIp);

        System.out.println("Enter the peer's port number:");
        int peerPort;
        while (true) {
            try {
                peerPort = Integer.parseInt(scanner.next());
                if (peerPort > 0 && peerPort <= 65535) {
                    break;
                } else {
                    System.out.println("Invalid port number. Please enter a number between 1 and 65535:");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid integer for the port number:");
            }
        }
        networkSetting.setPeerPort(peerPort);

        networkSetting.initializePeerAddress();

        AtomicInteger expectedNumber = new AtomicInteger(0);
        AtomicBoolean firstTry = new AtomicBoolean(false);
        AtomicReference<String> atomicFileName = new AtomicReference<>("");
        AtomicReference<Double> smoothedRTT = new AtomicReference<>(100.0);
        Map<Integer, Object> receivedFragments = new HashMap<>();
        double alpha = 0.2;
        networkSetting.initializeSocket();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Application is shutting down. Cleaning up resources...");
            fileDescriptor.deleteTemporaryFile();
        }));


        long startTime = 0;
        AtomicInteger fragmentCounter = new AtomicInteger(0);
        Thread inputThread = createInputThread(
                message,
                fileDescriptor
        );
        Thread keepAliveThread = startKeepAliveThread();
        keepAliveThread.start();
        while (true) {
            if (!fragmentManager.getSendTimestamps().isEmpty()) {
                fragmentManager.getSendTimestamps().forEach((seqNumber, sendTime) -> {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - sendTime > 10000) {
                        System.out.println("Timeout detected for SeqNumber: " + seqNumber);
                        Optional<MyUDPProtocol> fragmentToResend = fragmentManager.getPendingFragments()
                                .stream()
                                .filter(fragment -> Utils.byteArrayToInt(fragment.getSequenceNumber()) == seqNumber)
                                .findFirst();
                        fragmentToResend.ifPresent(fragment -> {
                            try {
                                resendFragment(fragment);
                                fragmentManager.getSendTimestamps().put(seqNumber, currentTime);
                            } catch (IOException e) {
                                System.out.println("Failed to resend fragment: " + e.getMessage());
                            }
                        });
                    }
                });
            }

            byte[] receiveBuffer = new byte[1500];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            networkSetting.getSocket().receive(receivePacket);
            lastActivityTime = System.currentTimeMillis(); // Update activity timestamp

            int receivedLength = receivePacket.getLength();
            byte[] actualData = Arrays.copyOfRange(receiveBuffer, 0, receivedLength);
            MyUDPProtocol receivedProtocol = MyUDPProtocol.parseReceivedData(actualData);

            if (receivedProtocol == null) {
                continue;
            }
            if (receivedProtocol != null && keepAliveThread.isInterrupted()) {
                keepAliveThread.start();
            }

            byte messageTypeCode = receivedProtocol.getTypeOfMessage();
            TypeOfMessage messageType;

            try {
                messageType = fromCode(messageTypeCode); // Convert to enum
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid message type received: " + messageTypeCode);
                continue;
            }

            if (!isValidCRC(receivedProtocol)) {
                System.out.println("Checksum is not correct, repeating process...");
                fragmentManager.setFinalRequest(receivedProtocol);
                if (messageType == SE || messageType == SF) {
                    System.out.println("Fragment received with errors");
                }
                sendMessage(8, message.get(), fileDescriptor);
                continue;
            }

            switch (messageType) {
                case AN -> {
                    if (fragmentManager.getFinalRequest() != null) {
                        int clientSeq = Utils.byteArrayToInt(fragmentManager.getFinalRequest().getSequenceNumber());
                        int serverAck = Utils.byteArrayToInt(receivedProtocol.getSequenceNumber());
                        fragmentManager.setFinalRequest(receivedProtocol);
                        if (serverAck == clientSeq) {
                            System.out.println("Valid ACK received, proceeding to send CONFIRM.");

                            fragmentManager.clearPendingFragments(clientSeq);

                            if (fragmentManager.getFinalRequest().getFlags() == 0) {
                                startTime = System.currentTimeMillis();
                                sendMessage(4, message.get(),fileDescriptor);
                            } else {
                                startTime = System.currentTimeMillis();
                                sendMessage(14, message.get(),fileDescriptor);
                            }
                        } else {
                            System.out.println("Invalid ACK received. Expected " + (clientSeq + 1) + " but got " + serverAck);
                        }
                    }
                }
                case ST -> {
                    int receivedSeqNumber = Utils.byteArrayToInt(receivedProtocol.getSequenceNumber());
                    if (lastProcessedSeqNumber.get() == receivedSeqNumber) {
                        System.out.println("Duplicate START received. Ignoring.");
                        break;
                    }

                    // Process the START message and send ANSWER
                    lastProcessedSeqNumber.set(receivedSeqNumber);
                    fragmentManager.setFinalRequest(receivedProtocol);
                    sendMessage(3, message.get(), fileDescriptor);
                    startTime = System.currentTimeMillis();
                }
                case FI -> {
                    if (Utils.byteToInt(receivedProtocol.getFlags()) == 1 || Utils.byteToInt(receivedProtocol.getFlags()) == 3) {
                        long endTime = System.currentTimeMillis();
                        long duration = endTime - startTime;

                        fragmentManager.clearPendingFragments(Utils.byteArrayToInt(receivedProtocol.getSequenceNumber()));
                        float percent = 0.0f;
                        if (Utils.byteToInt(receivedProtocol.getFlags()) == 1) {
                            // Get save directory
                            System.out.println("Please enter 5 if you want to save file to another package other default one");
                            while(!fileManager.isFileWaiting()){
                                Thread.sleep(100);
                            }

                            String savePath = fileManager.getChangedDir().isEmpty()
                                    ? fileDescriptor.destinationFolder + "\\" + atomicFileName.get()
                                    : fileManager.getChangedDir() + "\\" + atomicFileName.get();

                            System.out.println("Received file, assembling and saving...");
                            System.out.println("Total file size: " + fileDescriptor.filePosition + " bytes");
                            System.out.println("Total transfer duration: " + duration + " ms");
                            System.out.println("File saved to: " + savePath);
                            percent = (float) receiver / (fileDescriptor.filePosition + fileDescriptor.fileNamePosition + (fragmentCounter.get() * 14));
                            fileDescriptor.finalizeFileTransfer(savePath);
                        } else if (Utils.byteToInt(receivedProtocol.getFlags()) == 3) {
                            String res = messageDescriptor.getCompleteMessage();
                            System.out.println("Received message");
                            System.out.println("Total message size: " + res.length() + " bytes");
                            System.out.println("Total transfer duration: " + duration + " ms");
                            System.out.println("RESULT IS: \n" + res);
                            percent = (float) receiver / (res.length() + (fragmentCounter.get() * 14));
                        }
                        System.out.println("Percent from all: " + (percent * 100));


                        sendMessage(7, message.get(),fileDescriptor);

                        if (!inputThread.isAlive()) {
                            inputThread = createInputThread(message, fileDescriptor); // Create a new thread instance
                            inputThread.start();
                            System.out.println("Input thread restarted.");
                        }

                        atomicFileName.set("");
                        receiver = 0;
                        smoothedRTT.set(100.0);
                        message.set(null);
                        expectedNumber.set(0);
                        fragmentManager.setEnd(false);
                        firstTry.set(false);
                        receivedFragments.clear();
                        fragmentCounter.set(0);

                        sendMessage(13, message.get(),fileDescriptor);
                        System.out.println("""
                        What do you want to send?
                        1 - Send File
                        2 - Send Message
                        3 - Change Destination Folder
                        4 - Disconnect and Reconnect
                        """);
                    } else {
                        long endTime = System.currentTimeMillis();
                        long duration = endTime - startTime;

                        fragmentManager.clearPendingFragments(Utils.byteArrayToInt(receivedProtocol.getSequenceNumber()));

                        float percent = 0;

                        if (Utils.byteToInt(receivedProtocol.getFlags()) == 2) {
                            String savePath = atomicFileName.get();
                            System.out.println("Received file, assembling and saving...");
                            long length = Base64.getDecoder().decode(message.get().split(";")[1].trim()).length;
                            long fullLength = message.get().split(";")[0].trim().length() + length;
                            System.out.println("Total file size: " + length + " bytes");
                            System.out.println("Total transfer duration: " + duration + " ms");
                            long numOfFrag = (long) Math.ceil((double) fullLength / fragmentManager.getHowMany());
                            System.out.println("Number of fragments: " + numOfFrag);
                            System.out.println("Normal length of fragments: " + fragmentManager.getHowMany());
                            System.out.println("Length of the last fragment: " + fragmentManager.getMinLengthOfFragment());
                            System.out.println("File saved to: " + message.get().split(";")[0]);

                            percent = (float) sender / (fullLength + numOfFrag*14);
                        } else if (Utils.byteToInt(receivedProtocol.getFlags()) == 0) {
                            System.out.println("Received message");
                            int numberOfFragments = (int) Math.ceil((double) message.get().length() / fragmentManager.getHowMany());
                            System.out.println("Total message size: " + message.get().length() + " bytes");
                            System.out.println("Number of fragments: " + numberOfFragments);
                            System.out.println("Normal length of fragments: " + fragmentManager.getHowMany());
                            System.out.println("Length of the last fragment: " + fragmentManager.getMinLengthOfFragment());
                            System.out.println("Total transfer duration: " + duration + " ms");

                            percent = (float) sender / (message.get().length() + numberOfFragments * 14);
                        }
                        System.out.println("Percent from all: " + (percent * 100));


                        atomicFileName.set("");
                        smoothedRTT.set(100.0);
                        message.set(null);
                        sender = 0;
                        expectedNumber.set(0);
                        fragmentManager.setEnd(false);
                        firstTry.set(false);
                        receivedFragments.clear();
                        fragmentCounter.set(0);

                        sendMessage(13, message.get(),fileDescriptor);
                        System.out.println("""
                        What do you want to send?
                        1 - Send File
                        2 - Send Message
                        3 - Change Destination Folder
                        4 - Disconnect and Reconnect
                        """);
                    }
                }
                case SE -> {
                    System.out.println("Fragment #" + (fragmentCounter.get() + 1) + " received.");
                    int fragmentSize = receivedProtocol.getData().getData().length();
                    System.out.println("Fragment size: " + fragmentSize);

                    System.out.println("Length of header from this fragment: " + MyUDPProtocol.lengthOfHeader(receivedProtocol));
                    System.out.println("Percent from this fragment: " + MyUDPProtocol.percentFromAllData(receivedProtocol));
                    receiver += MyUDPProtocol.lengthOfHeader(receivedProtocol);

                    int seq = Utils.byteArrayToInt(receivedProtocol.getSequenceNumber());

                    receivedFragments.put(seq, receivedProtocol.getData());

                    // Use expectedNumber to track which fragments are in sequence and need to be processed
                    while (receivedFragments.containsKey(expectedNumber.get())) {
                        Data receivedData = (Data) receivedFragments.get(expectedNumber.get());
                        messageDescriptor.appendFragmentToMessage(receivedData.getData());

                        // Increment expectedNumber by the fragment length to track the next expected sequence
                        receivedFragments.remove(expectedNumber.get()); // remove after processing
                        expectedNumber.incrementAndGet();
                    }

                    fragmentCounter.incrementAndGet();
                    System.out.println("Fragment received without errors");

                    fragmentManager.setFinalRequest(receivedProtocol);
                    sendMessage(5, message.get(),fileDescriptor);

                }
                case CM -> {
                    int seqNumber = Utils.byteArrayToInt(receivedProtocol.getSequenceNumber());
                    if (fragmentManager.getSendTimestamps().containsKey(seqNumber)) {
                        long ackReceiveTime = System.currentTimeMillis();
                        long rtt = ackReceiveTime - fragmentManager.getSendTimestamps().get(seqNumber);

                        double newSmoothedRtt = alpha * rtt + (1 - alpha) * smoothedRTT.get();
                        smoothedRTT.set(newSmoothedRtt);
                        int THRESHOLD_RTT = 100;

                        if (smoothedRTT.get() < THRESHOLD_RTT) {
                            fragmentManager.setWindowSize(fragmentManager.getWindowSize() + 1);
                            System.out.println("Increasing window size to: " + fragmentManager.getWindowSize());
                        } else {
                            if (fragmentManager.getWindowSize() > 1) {
                                fragmentManager.setWindowSize(fragmentManager.getWindowSize() - 1);
                                System.out.println("Decreasing window size to: " + fragmentManager.getWindowSize());
                            }
                        }

                        fragmentManager.getSendTimestamps().remove(seqNumber);
                    } else {
                        System.out.println("seqNumber " + seqNumber + " not found in sendTimestamps.");
                    }

                    fragmentManager.clearPendingFragments(seqNumber);

                    if (fragmentManager.getPendingFragments().isEmpty()) {
                        System.out.println("I don't have more fragments to send, so i should end");
                        fragmentManager.setFinalRequest(receivedProtocol);
                        sendMessage(6, message.get(),fileDescriptor);
                    } else {
                        fragmentManager.setFinalRequest(receivedProtocol);
                        fragmentManager.decrementWindowCounter();
                        sendMessage(4, message.get(),fileDescriptor);
                    }
                }
                case RE -> {
                    System.out.println("Ok, we need to resend message, because it was broken");
                    fragmentManager.setFinalRequest(receivedProtocol);
                    sendMessage(9, message.get(),fileDescriptor);
                }
                case KI -> {
                    sendMessage(12, message.get(), fileDescriptor);
                }
                case KR -> {
                    lastKeepAliveTime = System.currentTimeMillis(); // Reset timeout timer
                    heartbeatFailures = 0;

                    if (!inputThread.isAlive())
                        inputThread.start();

                    if (!fragmentManager.getPendingFragments().isEmpty()) {
                        fragmentManager.getPendingFragments().forEach((fragment) -> {
                            MyUDPProtocol resend = new MyUDPProtocol(fragment);
                            int crc = 0;
                            if (resend.getData() != null || resend.getDataFile() != null) {
                                crc = calculateCRC(new ProtocolForCRC(resend));
                            } else{
                                crc = calculateCRC(new ProtocolForCRCWithoutData(resend));
                            }
                            resend.setCheckSumCRC(Utils.intToByteArray(crc));

                            byte[] sendBuffer = resend.toByteArrayWithData();
                            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, networkSetting.getPeerAddress(), networkSetting.getPeerPort());
                            try {
                                networkSetting.getSocket().send(sendPacket);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            lastActivityTime = System.currentTimeMillis();
                        });
                    }
                }
                case SF -> {
                    System.out.println("Fragment #" + (fragmentCounter.get() + 1) + " received.");
                    System.out.println("Fragment size: " + receivedProtocol.getDataFile().toByteArray().length);

                    System.out.println("Length of header from this fragment: " + MyUDPProtocol.lengthOfHeader(receivedProtocol));
                    System.out.println("Percent from this fragment: " + MyUDPProtocol.percentFromAllData(receivedProtocol));
                    receiver += MyUDPProtocol.lengthOfHeader(receivedProtocol);

                    DataFile dataFile = receivedProtocol.getDataFile();
                    int seq = Utils.byteArrayToInt(receivedProtocol.getSequenceNumber());

                    // Add fragment to receivedFragments
                    receivedFragments.put(seq, dataFile);

                    // Use expectedNumber to process fragments in order
                    while (receivedFragments.containsKey(expectedNumber.get())) {
                        DataFile currentDataFile = (DataFile) receivedFragments.get(expectedNumber.get());

                        if (!currentDataFile.getFileName().isEmpty()) {
                            atomicFileName.updateAndGet(existingName -> existingName + currentDataFile.getFileName());
                        }

                        if (currentDataFile.getFileContent().length > 0 && !firstTry.get()) {
                            System.out.println("Initializing file with name: " + atomicFileName.get());
                            try {
                                fileDescriptor.initializeTemporaryFile(atomicFileName.get());
                                firstTry.set(true);
                            } catch (IOException e) {
                                System.out.println("Failed to initialize temporary file: " + e.getMessage());
                                return;
                            }
                        }

                        if (currentDataFile.getFileContent() != null) {
                            fileDescriptor.appendFragmentToFile(currentDataFile.getFileContent());
                        }

                        // Remove processed fragment and increment expectedNumber
                        receivedFragments.remove(expectedNumber.get());
                        expectedNumber.incrementAndGet();
                    }

                    fragmentCounter.incrementAndGet();
                    System.out.println("Fragment received without errors");

                    fragmentManager.setFinalRequest(receivedProtocol);
                    sendMessage(15, message.get(),fileDescriptor);
                }
                case SM -> {
                    int seqNumber = Utils.byteArrayToInt(receivedProtocol.getSequenceNumber());

                    if (fragmentManager.getSendTimestamps().containsKey(seqNumber)) {
                        long ackReceiveTime = System.currentTimeMillis();
                        long rtt = ackReceiveTime - fragmentManager.getSendTimestamps().get(seqNumber);

                        double newSmoothedRtt = alpha * rtt + (1 - alpha) * smoothedRTT.get();
                        smoothedRTT.set(newSmoothedRtt);
                        int THRESHOLD_RTT = 100;

                        if (smoothedRTT.get() < THRESHOLD_RTT) {
                            fragmentManager.setWindowSize(fragmentManager.getWindowSize() + 1);
                            System.out.println("Increasing window size to: " + fragmentManager.getWindowSize());
                        } else {
                            if (fragmentManager.getWindowSize() > 1) {
                                fragmentManager.setWindowSize(fragmentManager.getWindowSize() - 1);
                                System.out.println("Decreasing window size to: " + fragmentManager.getWindowSize());
                            }
                        }

                        fragmentManager.getSendTimestamps().remove(seqNumber);
                    } else {
                        System.out.println("Sequence number not found in sendTimestamps: " + seqNumber);
                    }

                    fragmentManager.clearPendingFragments(seqNumber);

                    if (fragmentManager.getPendingFragments().isEmpty() && fragmentManager.isEnd()) {
                        System.out.println("I don't have more fragments to send, so i should end");
                        fragmentManager.setFinalRequest(receivedProtocol);
                        sendMessage(6, message.get(),fileDescriptor);
                    } else {
                        System.out.println("Okay! I have more fragments to send, so i need to send another");
                        fragmentManager.setFinalRequest(receivedProtocol);
                        fragmentManager.decrementWindowCounter();
                        sendMessage(14, message.get(),fileDescriptor);
                    }
                }
                default ->
                        System.out.println("Unhandled message type: " + messageType);
            }
        }
    }

    /**
     * Resends a specific UDP fragment after a timeout or CRC failure.
     *
     * @param fragmentToResend The {@code MyUDPProtocol} fragment to resend.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void resendFragment(MyUDPProtocol fragmentToResend) throws IOException {
        int crc = 0;
        if (fragmentToResend.getData() != null || fragmentToResend.getDataFile() != null) {
            crc = calculateCRC(new ProtocolForCRC(fragmentToResend));
        } else{
            crc = calculateCRC(new ProtocolForCRCWithoutData(fragmentToResend));
        }
        fragmentToResend.setCheckSumCRC(Utils.intToByteArray(crc));

        byte[] sendBuffer = fragmentToResend.toByteArrayWithData();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, networkSetting.getPeerAddress(), networkSetting.getPeerPort());
        networkSetting.getSocket().send(sendPacket);
    }

    /**
     * Creates a thread for handling user input.
     *
     * @param message         A reference to the current message being sent.
     * @param fileDescriptor  An object to manage file-related operations.
     * @return A new {@code Thread} instance for handling user input.
     */
    private static Thread createInputThread(
            AtomicReference<String> message,
            com.dimon.managers.FileDescriptor fileDescriptor
    ) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (!Thread.currentThread().isInterrupted()) {
                    System.out.println("""
                        What do you want to send?
                        1 - Send File
                        2 - Send Message
                        3 - Change Destination Folder
                        4 - Disconnect and Reconnect
                        """);

                    String input = reader.readLine();
                    if (input == null || input.trim().isEmpty()) {
                        System.out.println("Invalid input. Please try again.");
                        continue;
                    }

                    switch (input.trim()) {
                        case "1" -> handleFileSend(reader, fileDescriptor, message);
                        case "2" -> handleTextMessageSend(reader, message);
                        case "3" -> handleDestinationFolderChange(reader, fileDescriptor);
                        case "4" -> handlePeerReconnect(reader);
                        case "5" -> changeDir(reader);
                        default -> System.out.println("Invalid option. Please select from 1-4.");
                    }

                }
            }  catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("Input thread interrupted.");
                } else {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Allows the user to change the default directory where files are saved.
     *
     * @param reader The {@code BufferedReader} for reading user input.
     * @throws IOException if an error occurs during input handling.
     */
    private static void changeDir(BufferedReader reader) throws IOException {
        System.out.println("Do you want to save the file to a directory other than the default one? (yes/no)");
        String choice = reader.readLine().trim();

        if (choice.equalsIgnoreCase("yes")) {
            System.out.println("Enter the directory path:");
            String changedDir = reader.readLine().trim();
            fileManager.setChangedDir(changedDir);
        } else {
            fileManager.setChangedDir("");
        }

        fileManager.setFileWaiting(true);
        fileManager.setIsDirectoryInputPending(new AtomicBoolean(false));
    }

    /**
     * Handles the process of sending a file to a peer.
     *
     * @param reader          The {@code BufferedReader} for reading user input.
     * @param fileDescriptor  An object to manage file-related operations.
     * @param message         A reference to the current message being sent.
     * @throws IOException if an error occurs during file handling or packet transmission.
     */
    private static void handleFileSend(BufferedReader reader, com.dimon.managers.FileDescriptor fileDescriptor, AtomicReference<String> message) throws IOException, InterruptedException {
        System.out.println("Do you want to send the first packet corrupted? (yes/no)");
        boolean corruptFirstPacket = reader.readLine().trim().equalsIgnoreCase("yes");
        fragmentManager.setSendCorruptedFirstPacket(corruptFirstPacket);

        System.out.println("Enter the path to the file:");
        String filePath = reader.readLine();
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            System.out.println("Invalid file path. Please try again.");
            return;
        }

        byte [] fileBytes = fileDescriptor.readFileToBytes(file);
        String messageContent = file.getName() + ";" + Base64.getEncoder().encodeToString(fileBytes);

        int howM = handleFragmentSize(reader);

        if (message.get() != null) {
            addToBuffer(messageContent, howM);
        } else {
            fragmentManager.setHowMany(howM);
            message.set(messageContent);
            sendMessage(0, message.get(), fileDescriptor);
        }
    }

    /**
     * Handles the process of sending a text message to a peer.
     *
     * @param reader  The {@code BufferedReader} for reading user input.
     * @param message A reference to the current message being sent.
     * @throws IOException if an error occurs during input handling or packet transmission.
     */
    private static void handleTextMessageSend(BufferedReader reader, AtomicReference<String> message) throws IOException, InterruptedException {
        System.out.println("Do you want to send the first packet corrupted? (yes/no)");
        boolean corruptFirstPacket = reader.readLine().trim().equalsIgnoreCase("yes");
        fragmentManager.setSendCorruptedFirstPacket(corruptFirstPacket);

        System.out.println("Enter your message:");
        String inputMessage = reader.readLine();

        int howM = handleFragmentSize(reader);

        if (message.get() != null) {
            addToBuffer(inputMessage, howM);
        } else {
            fragmentManager.setHowMany(howM);
            message.set(inputMessage);
            sendMessage(0, message.get(), new com.dimon.managers.FileDescriptor());
        }
    }

    /**
     * Adds a new object to the input buffer for future transmission.
     *
     * @param content  The content of the message or file to send.
     * @param howMany  The number of bytes per fragment.
     */
    private static void addToBuffer(String content, int howMany) {
        ObjectToSend objectToSend = new ObjectToSend();
        objectToSend.setSend(content);
        objectToSend.setHowManyBytes(howMany);
        bufferOfInput.add(objectToSend);
    }

    /**
     * Handles the process of changing the destination folder for saving files.
     *
     * @param reader          The {@code BufferedReader} for reading user input.
     * @param fileDescriptor  An object to manage file-related operations.
     * @throws IOException if an error occurs during input handling.
     */
    private static void handleDestinationFolderChange(BufferedReader reader, com.dimon.managers.FileDescriptor fileDescriptor) throws IOException {
        System.out.println("Enter your destination folder:");
        String destinationFolder = reader.readLine();
        fileDescriptor.setDestinationFolder(destinationFolder);
    }

    /**
     * Handles the process of disconnecting and reconnecting to another peer.
     *
     * @param reader The {@code BufferedReader} for reading user input.
     * @throws IOException if an error occurs during input handling.
     */
    private static void handlePeerReconnect(BufferedReader reader) throws IOException {
        System.out.println("Are you sure you want to disconnect and reconnect to another peer? (yes/no)");
        boolean reconnect = reader.readLine().trim().equalsIgnoreCase("yes");
        if (reconnect) {
            terminateConnection();
        }
    }

    /**
     * Handles the process of setting a custom fragment size for data transmission.
     *
     * @param reader The {@code BufferedReader} for reading user input.
     * @return The fragment size chosen by the user or the default size if no input is provided.
     * @throws IOException if an error occurs during input handling.
     */
    private static int handleFragmentSize(BufferedReader reader) throws IOException {
        System.out.println("Do you want to set a custom fragment size? (yes/no)");
        boolean customSize = reader.readLine().trim().equalsIgnoreCase("yes");
        int how = 0;
        if (customSize) {
            System.out.println("Enter the number of bytes per fragment (max: " + Constants.MAX_SIZE_FRAGMENT + "):");
            String inputSize = reader.readLine();
            try {
                int fragmentSize = Integer.parseInt(inputSize);
                if (fragmentSize > Constants.MAX_SIZE_FRAGMENT || fragmentSize <= 0) {
                    fragmentSize = Constants.MAX_SIZE_FRAGMENT;
                    System.out.println("Fragment size exceeds max. Set to " + Constants.MAX_SIZE_FRAGMENT);
                }
                how = fragmentSize;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Using default fragment size.");
            }
        } else {
            how = Constants.MAX_SIZE_FRAGMENT;
        }

        return how;
    }

    /**
     * Starts a thread to periodically send keep-alive messages to the peer.
     *
     * @return A {@code Thread} instance for the keep-alive mechanism.
     */
    private static Thread startKeepAliveThread() {
        return new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastActivityTime > Constants.KEEP_ALIVE_INTERVAL) {
                        // No activity for 5 seconds, send a heartbeat
                        MyUDPProtocol keepAliveMessage = Utils.createKeepAlive(null, (short) fragmentManager.getWindowSize());
                        byte[] sendBuffer = keepAliveMessage.toByteArrayWithData();
                        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, networkSetting.getPeerAddress(), networkSetting.getPeerPort());
                        networkSetting.getSocket().send(sendPacket);

                        System.out.println("Heartbeat message sent due to inactivity.");

                        Thread.sleep(Constants.KEEP_ALIVE_INTERVAL);
                        if (System.currentTimeMillis() - lastKeepAliveTime > Constants.TIMEOUT_THRESHOLD) {
                            heartbeatFailures++;
                            System.out.println("Heartbeat acknowledgment not received. Failures: " + heartbeatFailures);

                            if (heartbeatFailures >= Constants.HEARTBEAT_FAILURE_THRESHOLD) {
                                System.out.println("Connection terminated due to heartbeat failure.");
                                terminateConnection();
                                break;
                            }
                        }
                    }

                    // Sleep until the next check
                    Thread.sleep(Constants.KEEP_ALIVE_INTERVAL);
                } catch (IOException e) {
                    System.out.println("Failed to send keep-alive message: " + e.getMessage());
                } catch (InterruptedException e) {
                    System.out.println("Keep-alive thread interrupted.");
                    break;
                }
            }
        });
    }

    /**
     * Terminates the connection with the peer and closes the socket.
     */
    private static void  terminateConnection() {
        System.out.println("Terminating connection...");
        try {
            networkSetting.closeSocket();
            System.out.println("Connection terminated.");
            System.exit(1);
        } catch (Exception ignored) {

        }


    }

    /**
     * Sends a message to the peer based on the specified message type.
     *
     * @param count           The type of message to send, represented as an integer.
     * @param msg             The message content.
     * @param fileDescriptor  An object to manage file-related operations.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendMessage(int count, String msg, com.dimon.managers.FileDescriptor fileDescriptor) throws IOException, InterruptedException {
        switch (count) {
            case 0 -> sendStartMessage(msg);
            case 3 -> sendAnswerMessage();
            case 4 -> sendDataMessage(msg);
            case 5 -> sendConfirmationMessage(0, CM);
            case 6 -> sendEndTransferMessage();
            case 7 -> sendFinalConfirmationMessage(false);
            case 8 -> resendCorruptedMessage();
            case 9 -> resendSpecificMessage();
            case 14 -> sendFileFragment(msg, fileDescriptor);
            case 15 -> sendConfirmationMessage(1, SM);
            case 13 -> completeTransmission(fileDescriptor);
            case 12 -> sendKeepAliveReply();
            default -> System.out.println("Unknown message count: " + count);
        }
    }

    /**
     * Sends a keep-alive reply message to the peer.
     *
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendKeepAliveReply() throws IOException {
        MyUDPProtocol replyKeepAlive = Utils.createReplyKeepAlive(fragmentManager.getFinalRequest());
        sendProtocol(replyKeepAlive);
    }

    /**
     * Completes the current transmission and resets the connection state.
     *
     * @param fileDescriptor An object to manage file-related operations.
     * @throws IOException if an error occurs during transmission.
     */
    private static void completeTransmission(FileDescriptor fileDescriptor) throws IOException, InterruptedException {
        System.out.println("Transmission completed. Resetting connection and stopping Keep-Alive.");

        fragmentManager.reset();

        fileManager.setFileWaiting(false);

        if (!bufferOfInput.isEmpty()) {
            ObjectToSend nextObject = bufferOfInput.poll();
            if (nextObject != null) {
                fragmentManager.setHowMany(nextObject.getHowManyBytes());
                message.set(nextObject.getSend());
                fileDescriptor.reset();
                System.out.println("Starting new message from buffer.");
                sendMessage(0, message.get(), fileDescriptor);
            }
        } else {
            fragmentManager.setHowMany(0);
            fileDescriptor.reset();
            System.out.println("Buffer is empty. No more messages to send.");
        }
    }

    /**
     * Sends a file fragment to the peer as part of the transmission process.
     *
     * @param msg             The message containing the file name and content.
     * @param fileDescriptor  An object to manage file-related operations.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendFileFragment(String msg, FileDescriptor fileDescriptor) throws IOException {
        System.out.println("Sending file fragment...");
        String fileName = msg.split(";")[0];
        byte[] nameBytes = fileName.getBytes();
        byte[] fileContentBytes = Base64.getDecoder().decode(msg.split(";")[1]);

        while (fragmentManager.canSendMoreFragments() && (fileDescriptor.fileContentPosition < fileContentBytes.length || !fragmentManager.isHeaderSent())) {

            MyUDPProtocol request;
            int remainingSize = fragmentManager.getHowMany();
            ByteArrayOutputStream fragmentData = new ByteArrayOutputStream();
            DataFile file = null;
            ByteArrayOutputStream nameFragment = new ByteArrayOutputStream();

            // Process file name (header) if not sent
            if (remainingSize > 0 && !fragmentManager.isHeaderSent()) {
                int end = Math.min(fileDescriptor.fileNamePosition + remainingSize, nameBytes.length);
                nameFragment.write(Arrays.copyOfRange(nameBytes, fileDescriptor.fileNamePosition, end));
                remainingSize -= (end - fileDescriptor.fileNamePosition);
                fileDescriptor.fileNamePosition = end;

                if (fileDescriptor.fileNamePosition >= nameBytes.length) {
                    fragmentManager.markHeaderAsSent(); // Encapsulates logging and setting header state
                }
            }

            // Process file content
            if (remainingSize > 0 && fragmentManager.isHeaderSent()) {
                int end = Math.min(fileDescriptor.fileContentPosition + remainingSize, fileContentBytes.length);
                fragmentData.write(Arrays.copyOfRange(fileContentBytes, fileDescriptor.fileContentPosition, end));
                fileDescriptor.fileContentPosition = end;
            }

            file = new DataFile(
                    nameFragment.size() > 0 ? nameFragment.toString() : "",
                    fragmentData.toByteArray(),
                    nameFragment.size() > 0 ? nameFragment.toString().length() : 0
            );

            // Create the protocol packet
            request = Utils.sendFile(fragmentManager.getFinalRequest(), file, fragmentManager.getSeqNumberCounter(), (short) fragmentManager.getWindowSize());
            fragmentManager.updateMinFragmentSize(request.toByteArrayWithData().length);

            // Set CRC and prepare for sending
            int crcToSend = calculateCRC(new ProtocolForCRC(request));
            request.setCheckSumCRC(Utils.intToByteArray(crcToSend));

            if (fragmentManager.isSendCorruptedFirstPacket()) {
                request.setCheckSumCRC(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
                System.out.println("First packet will be sent corrupted.");
                fragmentManager.setSendCorruptedFirstPacket(false); // Reset corruption flag after first packet
            }

            sender += MyUDPProtocol.lengthOfHeader(request);
            System.out.println("Length of Header: " + MyUDPProtocol.lengthOfHeader(request));
            System.out.println("Percentage header from fragment: " + (float)(MyUDPProtocol.lengthOfHeader(request)) / (MyUDPProtocol.percentFromAllData(request)));
            sendProtocol(request);

            // Add the fragment using `addFragment`
            fragmentManager.addFragment(request, System.currentTimeMillis());

            // Break the loop if all file content is sent
            if (fileDescriptor.fileContentPosition >= fileContentBytes.length && fragmentManager.isHeaderSent()) {
                fragmentManager.setEnd(true);
                break;
            }
        }
    }

    /**
     * Sends a {@code MyUDPProtocol} packet to the peer.
     *
     * @param request The protocol object to send.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendProtocol(MyUDPProtocol request) throws IOException {
        byte [] sendBuffer = request.toByteArrayWithData();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, networkSetting.getPeerAddress(), networkSetting.getPeerPort());
        networkSetting.getSocket().send(sendPacket);
        lastActivityTime = System.currentTimeMillis();
        fragmentManager.setFinalRequest(request);
    }

    /**
     * Resends a specific message based on its sequence number.
     *
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void resendSpecificMessage() throws IOException {
        int seqNumberToResend = Utils.byteArrayToInt(fragmentManager.getFinalRequest().getSequenceNumber());

        Optional<MyUDPProtocol> packetToResend = fragmentManager.getPendingFragments().stream()
                .filter(packet -> Utils.byteArrayToInt(packet.getSequenceNumber()) == seqNumberToResend)
                .findFirst();

        if (packetToResend.isPresent()) {
            MyUDPProtocol resend = packetToResend.get();
            int crc = 0;
            if (resend.getData() != null || resend.getDataFile() != null) {
                crc = calculateCRC(new ProtocolForCRC(resend));
            } else{
                crc = calculateCRC(new ProtocolForCRCWithoutData(resend));
            }
            resend.setCheckSumCRC(Utils.intToByteArray(crc));

            sendProtocol(resend);
            System.out.println("Resent packet with SeqNumber: " + seqNumberToResend);
        } else {
            System.out.println("No packet found in PendingFragments with SeqNumber: " + seqNumberToResend);
        }
    }

    /**
     * Resends a corrupted message due to a CRC mismatch.
     *
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void resendCorruptedMessage() throws IOException {
        System.out.println("Resending msg due to CRC mismatch.");

        MyUDPProtocol resend = Utils.resendMessage(fragmentManager.getFinalRequest()); // Use the same sequence number
        fragmentManager.setFinalRequest(resend);
        sendProtocol(resend);
    }

    /**
     * Sends a final confirmation message to indicate the completion of transmission.
     *
     * @param b A flag indicating the type of confirmation.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendFinalConfirmationMessage(boolean b) throws IOException {
        MyUDPProtocol request = Utils.sendFinalMessageConf(fragmentManager.getFinalRequest(), b);
        fragmentManager.setFinalRequest(request);
        sendProtocol(request);
    }

    /**
     * Sends a message indicating the end of the data transfer process.
     *
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendEndTransferMessage() throws IOException {
        System.out.println("End transferring data to another user");

        boolean isFile = (fragmentManager.getFinalRequest().getFlags() == 1);

        MyUDPProtocol request = Utils.sendFinalMessage(fragmentManager.getFinalRequest(), isFile, (short) fragmentManager.getWindowSize());
        fragmentManager.setFinalRequest(request);
        fragmentManager.addFragment(request, System.currentTimeMillis());
        sendProtocol(request);
    }

    /**
     * Sends a confirmation message to acknowledge received data.
     *
     * @param flag           The confirmation flag.
     * @param typeOfMessage  The type of message being confirmed.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendConfirmationMessage(int flag, TypeOfMessage typeOfMessage) throws IOException {
        System.out.println("Sending Confirmation msg...");

        MyUDPProtocol request = Utils.sendConfirmationAboutTakenData(fragmentManager.getFinalRequest(), flag,
                typeOfMessage);

        if (fragmentManager.isSendCorruptedFirstPacket()) {
            request.setCheckSumCRC(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
            System.out.println("First confirmation packet will be sent corrupted.");
        }

        sendProtocol(request);
        fragmentManager.setFinalRequest(request);

    }

    /**
     * Sends a data message as part of the transmission process.
     *
     * @param msg The content of the data message.
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendDataMessage(String msg) throws IOException {
        System.out.println("Sending data fragments...");
        byte [] fullDataBytes = msg.getBytes();
        int fragmentSize = fragmentManager.getHowMany();
        int start = fragmentManager.getSeqNumberCounter() * fragmentSize;
        int end = Math.min(start + fragmentSize, fullDataBytes.length);

        while (start < fullDataBytes.length && fragmentManager.canSendMoreFragments()) {
            byte[] fragmentBytes = Arrays.copyOfRange(fullDataBytes, start, end);
            Data fragment = new Data(new String(fragmentBytes));

            MyUDPProtocol request = Utils.sendMessage(fragmentManager.getFinalRequest(), fragment, fragmentManager.getSeqNumberCounter(), (short) fragmentManager.getWindowSize());
            if (fragmentManager.isSendCorruptedFirstPacket()) {
                request.setCheckSumCRC(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
                System.out.println("First packet will be sent corrupted.");
                fragmentManager.setSendCorruptedFirstPacket(false);
            }
            fragmentManager.updateMinFragmentSize(fragmentBytes.length);
            fragmentManager.addFragment(request, System.currentTimeMillis());
            sender += MyUDPProtocol.lengthOfHeader(request);
            System.out.println("Length of Header: " + MyUDPProtocol.lengthOfHeader(request));
            System.out.println("Percentage header from fragment: " + (float)(MyUDPProtocol.lengthOfHeader(request)) / (MyUDPProtocol.percentFromAllData(request)));
            sendProtocol(request);
            fragmentManager.setFinalRequest(request);
            start = fragmentManager.getSeqNumberCounter() * fragmentSize;
            end = Math.min(start + fragmentSize, fullDataBytes.length);
        }
    }

    /**
     * Sends an answer message in response to a peer's request.
     *
     * @throws IOException if an error occurs during packet transmission.
     */
    private static void sendAnswerMessage() throws IOException {
        System.out.println("Sending ANSWER message...");
        MyUDPProtocol request = Utils.answerToStartedInit(fragmentManager.getFinalRequest());
        sendProtocol(request);
    }

    /**
     * Sends a start message to initiate a new transmission.
     *
     * @param msg The initial message content, including file or text data.
     * @throws IOException if an error occurs during packet transmission or acknowledgment.
     */
    private static void sendStartMessage(String msg) throws IOException {
        System.out.println("Sending START...");

        String [] mes = msg.split(";");
        boolean isFile = (mes.length == 2);

        Random random = new Random();
        int initialSeq = random.nextInt(Integer.MAX_VALUE);
        MyUDPProtocol request = Utils.startedInit(isFile, initialSeq);
        fragmentManager.setFinalRequest(request);
        sendProtocol(request);
    }
}

//D:\\PPI\\2024_RegSim_11.pdf
//D:\PPI\1_zadanie.pdf
//D:\\PPI\\NN3.pdf

//D:\PKS TEST DIRECTORY


//4908468 bytes
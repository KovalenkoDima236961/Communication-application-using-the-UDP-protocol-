package com.dimon.utils;

import com.dimon.protocol.MyUDPProtocol;
import com.dimon.protocol.ProtocolForCRC;
import com.dimon.protocol.ProtocolForCRCWithoutData;
import com.dimon.protocol.TypeOfMessage;
import com.dimon.dto.Data;
import com.dimon.dto.DataFile;

import java.util.*;

import static com.dimon.protocol.TypeOfMessage.*;

/**
 * Utility class providing helper methods for UDP communication.
 * Includes methods for converting data formats, creating protocol messages,
 * and managing message-related operations such as CRC calculations and sequence handling.
 */
public class Utils {

    private static final Random random = new Random();

    /**
     * Converts an integer to a 4-byte array.
     *
     * @param number the integer to convert.
     * @return the corresponding 4-byte array.
     */
    public static byte[] intToByteArray(int number) {
        byte[] sequenceByteArray = new byte[4];
        sequenceByteArray[0] = (byte) (number >> 24);
        sequenceByteArray[1] = (byte) (number >> 16);
        sequenceByteArray[2] = (byte) (number >> 8);
        sequenceByteArray[3] = (byte) (number);

        return sequenceByteArray;
    }

    public static byte[] shortToByteArray(short number) {
        return new byte[] {
                (byte) (number >> 8),  // Higher byte
                (byte) (number)       // Lower byte
        };
    }

    public static short byteArrayToShort(byte[] byteArray) {
        if (byteArray == null || byteArray.length != 2) {
            throw new IllegalArgumentException("byteArray must be a non-null array of length 2");
        }
        return (short) ((byteArray[0] << 8) | (byteArray[1] & 0xFF));
    }


    /**
     * Converts a 4-byte array to an integer.
     *
     * @param byteArray the 4-byte array to convert.
     * @return the corresponding integer.
     */
    public static int byteArrayToInt(byte[] byteArray) {
        return ((byteArray[0] & 0xFF) << 24) |
                ((byteArray[1] & 0xFF) << 16) |
                ((byteArray[2] & 0xFF) << 8) |
                (byteArray[3] & 0xFF);
    }

    /**
     * Converts a single byte to an integer.
     *
     * @param flag the byte to convert.
     * @return the corresponding integer.
     */
    public static int byteToInt(byte flag) {
        return (int) flag;
    }

    /**
     * Creates a START message for initiating communication.
     *
     * @param isFile               whether the message is for file transmission.
     * @param initialSequenceNumber the initial sequence number for the message.
     * @return the created START message.
     */
    public static MyUDPProtocol startedInit(boolean isFile, int initialSequenceNumber) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        udpProtocol.setSequenceNumber(intToByteArray(initialSequenceNumber));

        udpProtocol.setTypeOfMessage(ST.getCode());
        udpProtocol.setWindow(shortToByteArray((short) 4));
        udpProtocol.setFlags(isFile ?(byte)  1 :(byte)  0);

        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int crc = CRC.calculateCRC(new ProtocolForCRCWithoutData(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(crc));

        return udpProtocol;
    }

    /**
     * Creates an ANSWER message in response to a START message.
     *
     * @param received the received START message.
     * @return the created ANSWER message.
     */
    public static MyUDPProtocol answerToStartedInit(MyUDPProtocol received) {
        if (received == null) {
            System.out.println("Error: received protocol is null");
            return null;
        }

        MyUDPProtocol udpProtocol = new MyUDPProtocol();
        udpProtocol.setSequenceNumber(received.getSequenceNumber());

        udpProtocol.setTypeOfMessage(AN.getCode());  // ACK message type
        udpProtocol.setWindow(received.getWindow());
        udpProtocol.setFlags((byte) received.getFlags());
        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int val = CRC.calculateCRC(new ProtocolForCRCWithoutData(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(val));

        return udpProtocol;
    }

    /**
     * Creates a SEND message containing a data fragment.
     *
     * @param received       the received message for context.
     * @param fragment       the data fragment to send.
     * @param seqNumberCounter the sequence number of the fragment.
     * @return the created SEND message.
     */
    public static MyUDPProtocol sendMessage(MyUDPProtocol received, Data fragment, int seqNumberCounter, short windowSize) {

        MyUDPProtocol udpProtocol = new MyUDPProtocol();
        udpProtocol.setSequenceNumber(intToByteArray(seqNumberCounter));

        udpProtocol.setTypeOfMessage(TypeOfMessage.SE.getCode());
        udpProtocol.setWindow(Utils.shortToByteArray(windowSize));
        udpProtocol.setFlags((byte) 0);
        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(fragment);
        udpProtocol.setDataFile(null);

        int val = CRC.calculateCRC(new ProtocolForCRC(udpProtocol));
        udpProtocol.setCheckSumCRC(Utils.intToByteArray(val));

        return udpProtocol;
    }

    /**
     * Creates a CONFIRMATION message indicating successful receipt of data.
     *
     * @param received      the received message for context.
     * @param flag          a flag indicating additional metadata.
     * @param typeOfMessage the type of confirmation message.
     * @return the created CONFIRMATION message.
     */
    public static MyUDPProtocol sendConfirmationAboutTakenData(MyUDPProtocol received, int flag, TypeOfMessage typeOfMessage) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        udpProtocol.setSequenceNumber(received.getSequenceNumber());

        udpProtocol.setTypeOfMessage(typeOfMessage.getCode());
        udpProtocol.setWindow(received.getWindow());
        udpProtocol.setFlags((byte) flag);

        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int val = CRC.calculateCRC(new ProtocolForCRCWithoutData(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(val));

        return udpProtocol;
    }
    /**
     * Creates a SEND FILE message for file transfer.
     *
     * @param received         the previously received protocol for context.
     * @param file             the file to be sent.
     * @param seqNumberCounter the sequence number of the file fragment.
     * @return a `MyUDPProtocol` object representing the SEND FILE message.
     */
    public static MyUDPProtocol sendFile(MyUDPProtocol received, DataFile file, int seqNumberCounter, short windowSize) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        udpProtocol.setSequenceNumber(Utils.intToByteArray(seqNumberCounter));

        udpProtocol.setTypeOfMessage(SF.getCode());

        udpProtocol.setWindow(Utils.shortToByteArray(windowSize));

        udpProtocol.setFlags((byte) 1);

        udpProtocol.setLengthOfFileName(Utils.intToByteArray(file.getNameLength()));

        udpProtocol.setDataFile(file);

        udpProtocol.setData(null);

        return udpProtocol;
    }

    /**
     * Creates a FINISH message to signify the end of communication.
     *
     * @param receiver the previously received protocol for context.
     * @param isFile   whether the communication was related to a file transfer.
     * @return a `MyUDPProtocol` object representing the FINISH message.
     */
    public static MyUDPProtocol sendFinalMessage(MyUDPProtocol receiver, boolean isFile, short windowSize) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        int receivedSeqNumber = byteArrayToInt(receiver.getSequenceNumber());
        int newSeqNumber = receivedSeqNumber + 1;
        udpProtocol.setSequenceNumber(intToByteArray(newSeqNumber));

        udpProtocol.setTypeOfMessage(FI.getCode());
        udpProtocol.setWindow(Utils.shortToByteArray(windowSize));

        udpProtocol.setFlags(isFile ? (byte) 1 : (byte) 3);

        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int crcValue = CRC.calculateCRC(new ProtocolForCRC(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(crcValue));

        return udpProtocol;
    }
    /**
     * Creates a RESEND message in case of CRC mismatch or lost packets.
     *
     * @param receiver the protocol to base the resend request on.
     * @return a `MyUDPProtocol` object representing the RESEND message.
     */
    public static MyUDPProtocol resendMessage(MyUDPProtocol receiver) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        udpProtocol.setSequenceNumber(receiver.getSequenceNumber());

        udpProtocol.setTypeOfMessage(RE.getCode());

        udpProtocol.setWindow(receiver.getWindow());
        udpProtocol.setFlags((byte) receiver.getFlags());

        udpProtocol.setData(null);

        udpProtocol.setDataFile(null);

        int crcValue = CRC.calculateCRC(new ProtocolForCRC(udpProtocol));
        udpProtocol.setCheckSumCRC(Utils.intToByteArray(crcValue));

        return udpProtocol;
    }
    /**
     * Creates a FINISH CONFIRMATION message to signify the end of communication.
     *
     * @param receiver the previously received protocol for context.
     * @param isFile   whether the communication was related to a file transfer.
     * @return a `MyUDPProtocol` object representing the FINISH message.
     */
    public static MyUDPProtocol sendFinalMessageConf(MyUDPProtocol receiver, boolean isFile) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();


        udpProtocol.setSequenceNumber(receiver.getSequenceNumber());

        udpProtocol.setTypeOfMessage(TypeOfMessage.FI.getCode());
        udpProtocol.setWindow(receiver.getWindow());
        boolean flag = receiver.getFlags() == 1;
        udpProtocol.setFlags(flag ? (byte) 2 : (byte) 0);

        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int crcValue = CRC.calculateCRC(new ProtocolForCRC(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(crcValue));

        return udpProtocol;
    }

    /**
     * Creates a KEEP ALIVE message to maintain a connection.
     *
     * @param prev the previous protocol message, if available.
     * @return a `MyUDPProtocol` object representing the KEEP ALIVE message.
     */
    public static MyUDPProtocol createKeepAlive(MyUDPProtocol prev, short windowSize) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        int sequence = (prev != null && prev.getSequenceNumber() != null)
                ? byteArrayToInt(prev.getSequenceNumber()) + 1
                : 0;


        udpProtocol.setSequenceNumber(intToByteArray(sequence));
        udpProtocol.setTypeOfMessage(TypeOfMessage.KI.getCode());
        udpProtocol.setWindow(shortToByteArray(windowSize));
        udpProtocol.setFlags((byte)0);
        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int checksum = CRC.calculateCRC(new ProtocolForCRCWithoutData(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(checksum));

        return udpProtocol;
    }

    /**
     * Creates a REPLY KEEP ALIVE message in response to a KEEP ALIVE message.
     *
     * @param prev the previous KEEP ALIVE protocol.
     * @return a `MyUDPProtocol` object representing the REPLY KEEP ALIVE message.
     */
    public static MyUDPProtocol createReplyKeepAlive(MyUDPProtocol prev) {
        MyUDPProtocol udpProtocol = new MyUDPProtocol();

        int sequence = (prev != null && prev.getSequenceNumber() != null)
                ? byteArrayToInt(prev.getSequenceNumber()) + 1
                : random.nextInt();

        udpProtocol.setSequenceNumber(intToByteArray(sequence));
        udpProtocol.setTypeOfMessage(TypeOfMessage.KR.getCode());
        udpProtocol.setWindow(shortToByteArray((short) 4));
        udpProtocol.setFlags(0);
        udpProtocol.setLengthOfFileName(new byte[2]);
        udpProtocol.setData(null);
        udpProtocol.setDataFile(null);

        int checksum = CRC.calculateCRC(new ProtocolForCRCWithoutData(udpProtocol));
        udpProtocol.setCheckSumCRC(intToByteArray(checksum));

        return udpProtocol;
    }
}
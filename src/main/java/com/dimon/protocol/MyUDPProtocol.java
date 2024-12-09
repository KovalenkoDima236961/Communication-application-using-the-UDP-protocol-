package com.dimon.protocol;

import com.dimon.dto.Data;
import com.dimon.dto.DataFile;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

/**
 * Represents a custom UDP protocol for transmitting data. This class
 * defines the structure of the protocol, including fields for sequence numbers,
 * type of message, checksum, window size, flags, and data. It also provides
 * methods for serialization and deserialization of the protocol into byte arrays.
 * <p>
 * This class supports handling both message and file data, including converting
 * protocol objects to byte arrays and parsing byte arrays into protocol objects.
 * </p>
 * <p>
 * Features include:
 * - Handling sequence numbers and type of messages.
 * - Generating byte arrays with optional data or file content.
 * - Support for checksum validation and flags.
 * </p>
 */
@Getter
@Setter
public class MyUDPProtocol {
    private byte [] sequenceNumber = new byte[4]; // 4-byte sequence number
    private byte typeOfMessage;                   // Single byte type of message
    private byte [] checkSumCRC = new byte[4];    // 4-byte checksum (CRC)
    private byte [] window = new byte[2];         // 2-byte window size
    private byte flags;                            // Flags field (2 bits used)
    private byte [] lengthOfFileName = new byte[2]; // 2-byte length of the file name
    private Data data;                               // Data for message transmission
    private DataFile dataFile;                        // Data for message transmission

    public MyUDPProtocol() {}

    /**
     * Copy constructor for creating a deep copy of an existing {@code MyUDPProtocol}.
     *
     * @param request the protocol instance to copy.
     */
    public MyUDPProtocol(MyUDPProtocol request) {
        this.sequenceNumber = request.sequenceNumber != null ? Arrays.copyOf(request.sequenceNumber, request.sequenceNumber.length) : null;
        this.typeOfMessage = request.getTypeOfMessage();
        this.checkSumCRC = request.checkSumCRC != null ? Arrays.copyOf(request.checkSumCRC, request.checkSumCRC.length) : null;
        this.window = request.window != null ? Arrays.copyOf(request.window, request.window.length) : null;
        this.lengthOfFileName = request.lengthOfFileName;
        this.flags = request.flags;
        this.data = request.data != null ? request.data.clone() : null;
        this.dataFile = request.dataFile != null ? new DataFile(request.dataFile) : null;
    }

    /**
     * Sets the flags field, ensuring the value is within a valid range.
     *
     * @param value the flag value (0-3).
     * @throws IllegalArgumentException if the value is out of range.
     */
    public void setFlags(int value) {
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException("Value must be between 0 and 3 for 2 bits.");
        }
        this.flags = (byte) value;
    }

    /**
     * Converts the protocol instance into a byte array representation.
     *
     * @return a byte array containing the serialized protocol fields.
     */
    public byte [] toByteArray() {
        byte [] data = new byte[4+1+4+2+1+2];

        int index = 0;

        System.arraycopy(sequenceNumber, 0, data, index, 4);
        index += 4;

        // Копіюємо typeOfMessage
        data[index++] = typeOfMessage;

        // Копіюємо checkSumCRC
        System.arraycopy(checkSumCRC, 0, data, index, 4);  // Adjust for 8 bytes
        index += 4;

        // Копіюємо window
        System.arraycopy(window, 0, data, index, 2);
        index += 2;

        // Копіюємо flags
        data[index++] = flags;

        System.arraycopy(lengthOfFileName,0, data, index, 2);

        return data;
    }

    /**
     * Converts the protocol into a byte array, including data if present.
     *
     * @return a byte array containing the serialized protocol and its data.
     */
    public byte [] toByteArrayWithData() {
        if (dataFile != null) {
            return toByteArrayWithFileData();
        } else if (data != null) {
            return toByteArrayWithMessage();
        } else {
            return toByteArray();
        }
    }

    /**
     * Converts the protocol into a byte array, including message-specific data.
     *
     * @return a byte array containing the serialized protocol with message data.
     */
    private byte[] toByteArrayWithMessage() {
        byte [] messageBytes = data.convertDataToByteArray();

        byte [] fullData = new byte[4+1+4+2+1+2+messageBytes.length];
        int index = 0;

        System.arraycopy(sequenceNumber, 0, fullData, index, 4);
        index += 4;

        // Копіюємо typeOfMessage
        fullData[index++] = typeOfMessage;

        System.arraycopy(checkSumCRC, 0, fullData, index, 4);
        index += 4;

        System.arraycopy(window, 0, fullData, index, 2);
        index += 2;

        fullData[index++] = flags;
        fullData[index++] = 0;
        fullData[index++] = 0;

        System.arraycopy(messageBytes, 0, fullData, index, messageBytes.length);

        return fullData;
    }


    /**
     * Converts the protocol into a byte array, including file-specific data.
     *
     * @return a byte array containing the serialized protocol with file data.
     */
    private byte[] toByteArrayWithFileData() {
        byte [] nameBytes = dataFile.getFileName().getBytes();
        byte [] fileContentBytes = dataFile.getFileContent();

        lengthOfFileName = intToTwoBytes(nameBytes.length);

        byte [] fullData = new byte[4+1+4+2+1+2+nameBytes.length+fileContentBytes.length];
        int index = 0;

        System.arraycopy(sequenceNumber, 0, fullData, index, 4);
        index += 4;

        // Копіюємо typeOfMessage
        fullData[index++] = typeOfMessage;

        System.arraycopy(checkSumCRC, 0, fullData, index, 4);
        index += 4;

        System.arraycopy(window, 0, fullData, index, 2);
        index += 2;

        fullData[index++] = flags;

        System.arraycopy(lengthOfFileName, 0, fullData, index, 2);
        index += 2;

        System.arraycopy(nameBytes, 0, fullData, index, nameBytes.length);
        index += nameBytes.length;

        System.arraycopy(fileContentBytes, 0, fullData, index, fileContentBytes.length);

        return fullData;
    }

    /**
     * Converts an integer into a 2-byte array representation.
     *
     * @param number the integer value to convert.
     * @return a 2-byte array representing the value.
     */
    public static byte[] intToTwoBytes(int number) {
        return new byte[] {
                (byte) (number >> 8),
                (byte) number
        };
    }
    /**
     * Converts a 2-byte array into an integer representation.
     *
     * @param bytes the 2-byte array to convert.
     * @return the integer value represented by the byte array.
     */
    public static int twoBytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    @Override
    public String toString() {
        return "MyUDPProtocol{" +
                "sequenceNumber=" + Arrays.toString(sequenceNumber) +
                ", typeOfMessage=" + (typeOfMessage) +
                ", checkSumCRC=" + Arrays.toString(checkSumCRC) +
                ", window=" + Arrays.toString(window) +
                ", flags=" + flags +
                ", data=" + data +
                '}';
    }

    /**
     * Parses a byte array to create a {@code MyUDPProtocol} instance with message data.
     *
     * @param data the byte array to parse.
     * @return a {@code MyUDPProtocol} instance.
     */
    public static MyUDPProtocol fromByteArrayWithMessage(byte [] data) {
        MyUDPProtocol protocol = new MyUDPProtocol();
        int index = 0;

        // Читаємо sequenceNumber
        System.arraycopy(data, index, protocol.sequenceNumber, 0, 4);
        index += 4;

        // Копіюємо typeOfMessage
        protocol.typeOfMessage = data[index++];

        // Читаємо checkSumCRC
        System.arraycopy(data, index, protocol.checkSumCRC, 0, 4);
        index += 4;

        // Читаємо window
        System.arraycopy(data, index, protocol.window, 0, 2);
        index += 2;

        // Читаємо flags
        protocol.flags = data[index++];

        // The last one is Data
        byte[] messageBytes = Arrays.copyOfRange(data, index, data.length);  // Copy only valid part
        String cleanedMessage = removeNullBytes(messageBytes);  // Видаляємо лише нуль-байти
        protocol.setData(new Data(cleanedMessage));  // Конвертуємо у Data

        return protocol;
    }
    /**
     * Parses a received byte array to create a {@code MyUDPProtocol} instance.
     *
     * @param data the byte array to parse.
     * @return a {@code MyUDPProtocol} instance.
     */
    public static MyUDPProtocol parseReceivedData(byte[] data) {
        try {
            byte typeCode = data[4];
            TypeOfMessage typeOfMessage = TypeOfMessage.fromCode(typeCode);

            switch (typeOfMessage) {
                case SF -> {
                    return MyUDPProtocol.fromByteArrayWithFileData(data);
                }
                case SE -> {
                    return MyUDPProtocol.fromByteArrayWithMessage(data);
                }
                default -> {
                    return MyUDPProtocol.fromByteArrayWithoutData(data);
                }
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid typeOfMessage received. Data parsing failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses a byte array to create a {@code MyUDPProtocol} instance without additional data.
     *
     * @param data the byte array to parse.
     * @return a {@code MyUDPProtocol} instance without additional data.
     */
    private static MyUDPProtocol fromByteArrayWithoutData(byte[] data) {
        MyUDPProtocol protocol = new MyUDPProtocol();
        int index = 0;

        System.arraycopy(data, index, protocol.sequenceNumber, 0, 4);
        index += 4;

        protocol.typeOfMessage = data[index++];

        System.arraycopy(data, index, protocol.checkSumCRC, 0, 4);
        index += 4;

        System.arraycopy(data, index, protocol.window, 0, 2);
        index+=2;

        protocol.flags = data[index++];

        System.arraycopy(data, index, protocol.lengthOfFileName, 0, 2);

        protocol.data = null;
        protocol.dataFile = null;

        return protocol;
    }

    /**
     * Parses a byte array to create a {@code MyUDPProtocol} instance with file data.
     *
     * @param data the byte array to parse.
     * @return a {@code MyUDPProtocol} instance.
     */
    public static MyUDPProtocol fromByteArrayWithFileData(byte [] data) {
        MyUDPProtocol protocol = new MyUDPProtocol();
        int index = 0;

        System.arraycopy(data, index, protocol.sequenceNumber, 0, 4);
        index+=4;

        protocol.typeOfMessage = data[index++];

        System.arraycopy(data, index, protocol.checkSumCRC, 0, 4);
        index+=4;

        System.arraycopy(data, index, protocol.window, 0, 2);
        index+=2;

        protocol.flags = data[index++];

        System.arraycopy(data, index, protocol.lengthOfFileName, 0, 2);
        index+=2;

        int nameLength = twoBytesToInt(protocol.lengthOfFileName);

        byte[] nameBytes = Arrays.copyOfRange(data, index, index + nameLength);
        index += nameLength;

        int fileContentLength = data.length - index;
        byte[] fileContentBytes = new byte[fileContentLength];
        System.arraycopy(data, index, fileContentBytes, 0, fileContentLength);

        protocol.dataFile = new DataFile(
                new String(nameBytes),
                fileContentBytes,
                nameBytes.length
        );

        return protocol;
    }
    /**
     * Utility method to remove null bytes from a byte array.
     *
     * @param byteArray the byte array to clean.
     * @return a {@code String} with null bytes removed.
     */
    public static String removeNullBytes(byte[] byteArray) {
        StringBuilder result = new StringBuilder();
        for (byte b : byteArray) {
            if (b != 0) {
                result.append((char) b);
            }
        }
        return result.toString();
    }

    public static int lengthOfHeader(MyUDPProtocol udpProtocol) {
        return 14;
    }

    public static float percentFromAllData(MyUDPProtocol udpProtocol) {
        int lengthOfData = udpProtocol.getData() == null ? udpProtocol.getDataFile().getNameLength() + udpProtocol.getDataFile().getFileContent().length : udpProtocol.getData().getData().length();
        return (float) lengthOfHeader(udpProtocol) / (lengthOfData + 14);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MyUDPProtocol that = (MyUDPProtocol) obj;
        return Arrays.equals(this.sequenceNumber, that.sequenceNumber);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.sequenceNumber);
    }
}
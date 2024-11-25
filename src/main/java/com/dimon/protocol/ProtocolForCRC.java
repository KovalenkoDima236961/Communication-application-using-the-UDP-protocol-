package com.dimon.protocol;

import com.dimon.dto.Data;
import com.dimon.dto.DataFile;

/**
 * Represents a protocol structure specifically designed for CRC (Cyclic Redundancy Check) calculations.
 * This class extracts and formats the necessary fields from a `MyUDPProtocol` object to prepare
 * a byte array suitable for generating a CRC checksum.
 */
public class ProtocolForCRC {

    /**
     * The 4-byte sequence number identifying the packet's order.
     */
    private byte[] sequenceNumber = new byte[4];

    /**
     * The type of message being transmitted, represented as a single byte.
     */
    private byte typeOfMessage;

    /**
     * The 2-byte sliding window size for flow control.
     */
    private byte[] window = new byte[2];

    /**
     * The flags associated with the packet, represented as a single byte.
     */
    private byte flags;

    /**
     * The 2-byte field representing the length of the file name (if applicable).
     */
    private byte[] lengthOfName = new byte[2];

    /**
     * The message data payload (if present).
     */
    private Data data;

    /**
     * The file data payload (if present).
     */
    private DataFile dataFile;

    /**
     * Constructs a `ProtocolForCRC` object by extracting relevant fields from a `MyUDPProtocol` object.
     *
     * @param myUDPProtocol the source `MyUDPProtocol` object to extract data from.
     */
    public ProtocolForCRC(MyUDPProtocol myUDPProtocol) {
        this.sequenceNumber = myUDPProtocol.getSequenceNumber();
        this.typeOfMessage = myUDPProtocol.getTypeOfMessage();
        this.window = myUDPProtocol.getWindow();
        this.flags = myUDPProtocol.getFlags();
        this.lengthOfName = myUDPProtocol.getLengthOfFileName();
        this.data = myUDPProtocol.getData();
        this.dataFile = myUDPProtocol.getDataFile();
    }

    /**
     * Serializes the protocol fields into a byte array, including message or file data if present.
     *
     * @return the serialized byte array representation of the protocol.
     */
    public byte[] toByteArray() {
        int dataSize = 4 + 1 + 2 + 1 + 2;

        if (this.data != null) {
            dataSize += this.data.getData().getBytes().length;
        }

        if (this.dataFile != null) {
            dataSize += dataFile.getFileName().getBytes().length;
            dataSize += dataFile.getFileContent().length;
        }

        byte[] data = new byte[dataSize];
        int index = 0;

        System.arraycopy(sequenceNumber, 0, data, index, 4);
        index += 4;

        data[index++] = typeOfMessage;

        System.arraycopy(window, 0, data, index, 2);
        index += 2;

        data[index++] = flags;

        System.arraycopy(lengthOfName, 0, data, index, 2);
        index += 2;

        if (this.data != null) {
            byte[] messageBytes = this.data.getData().getBytes();
            System.arraycopy(messageBytes, 0, data, index, messageBytes.length);
            index += messageBytes.length;
        }

        if (this.dataFile != null) {
            byte[] nameBytes = dataFile.getFileName().getBytes();
            byte[] contentBytes = dataFile.getFileContent();

            System.arraycopy(nameBytes, 0, data, index, nameBytes.length);
            index += nameBytes.length;

            System.arraycopy(contentBytes, 0, data, index, contentBytes.length);
        }

        return data;
    }
}

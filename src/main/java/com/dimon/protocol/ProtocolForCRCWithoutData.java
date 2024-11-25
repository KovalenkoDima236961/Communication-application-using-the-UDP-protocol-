package com.dimon.protocol;

/**
 * Represents a protocol structure for CRC (Cyclic Redundancy Check) calculations without data or file content.
 * This class extracts and formats the necessary fields from a `MyUDPProtocol` object, excluding optional data.
 */
public class ProtocolForCRCWithoutData {

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
     * Constructs a `ProtocolForCRCWithoutData` object by extracting relevant fields from a `MyUDPProtocol` object,
     * excluding any optional data or file content.
     *
     * @param myUDPProtocol the source `MyUDPProtocol` object to extract data from.
     */
    public ProtocolForCRCWithoutData(MyUDPProtocol myUDPProtocol) {
        this.sequenceNumber = myUDPProtocol.getSequenceNumber();
        this.typeOfMessage = myUDPProtocol.getTypeOfMessage();
        this.window = myUDPProtocol.getWindow();
        this.flags = myUDPProtocol.getFlags();
        this.lengthOfName = myUDPProtocol.getLengthOfFileName();
    }

    /**
     * Serializes the protocol fields into a byte array.
     * Excludes any optional data or file content.
     *
     * @return the serialized byte array representation of the protocol.
     */
    public byte[] toByteArray() {
        byte[] data = new byte[4 + 1 + 2 + 1 + 2];

        int index = 0;

        // Add sequence number
        System.arraycopy(sequenceNumber, 0, data, index, 4);
        index += 4;

        // Add type of message
        data[index++] = typeOfMessage;

        // Add window size
        System.arraycopy(window, 0, data, index, 2);
        index += 2;

        // Add flags
        data[index++] = flags;

        // Add length of name
        System.arraycopy(lengthOfName, 0, data, index, 2);

        return data;
    }
}

package com.dimon.protocol;

import lombok.Getter;

/**
 * Enum representing different types of messages in the UDP communication protocol.
 * Each message type is associated with a unique byte code.
 */
@Getter
public enum TypeOfMessage {

    /**
     * Start message, used to initiate a communication session.
     */
    ST((byte) 0), // START

    /**
     * Answer message, used to acknowledge a start or similar request.
     */
    AN((byte) 1), // ANSWER

    /**
     * Finish message, used to signify the end of a session or communication.
     */
    FI((byte) 2), // FINISH

    /**
     * Send message, used to send data or requests.
     */
    SE((byte) 3), // SEND

    /**
     * Confirm message, used to confirm the receipt of a message.
     */
    CM((byte) 4), // CONFIRM MESSAGE

    /**
     * Resend message, used to request the retransmission of a message.
     */
    RE((byte) 5), // RESEND MESSAGE

    /**
     * Keep-alive message, used to maintain an active connection.
     */
    KI((byte) 6), // KEEP ALIVE

    /**
     * Reply to a keep-alive message.
     */
    KR((byte) 7), // REPLY KEEP ALIVE

    /**
     * Send file message, used to initiate file transmission.
     */
    SF((byte) 8), // SEND FILE

    /**
     * Confirm file message, used to confirm the receipt of file fragments.
     */
    SM((byte) 9); // CONFIRM FILE

    /**
     * The byte code associated with the message type.
     */
    private final byte code;

    /**
     * Constructor to associate the byte code with the enum constant.
     *
     * @param code the byte code representing the message type.
     */
    TypeOfMessage(byte code) {
        this.code = code;
    }

    /**
     * Retrieves the enum constant corresponding to the given byte code.
     *
     * @param code the byte code of the message type.
     * @return the corresponding `TypeOfMessage` enum constant.
     * @throws IllegalArgumentException if the code does not match any message type.
     */
    public static TypeOfMessage fromCode(byte code) {
        for (TypeOfMessage type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}

package com.dimon.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an object prepared for transmission in the UDP communication protocol.
 * Encapsulates the size of the data and the data itself.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ObjectToSend {

    /**
     * The number of bytes in the object to be sent.
     */
    private int howManyBytes;

    /**
     * The object containing the data to be transmitted.
     */
    private String send;
}

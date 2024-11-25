package com.dimon.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a wrapper for string data that can be converted to a byte array.
 * This class is designed to handle protocol data encapsulation and supports cloning.
 */
@lombok.Data
@AllArgsConstructor
@NoArgsConstructor
public class Data implements Cloneable {
    /**
     * The string representation of the data.
     */
    private String data;

    /**
     * Converts the string data into a byte array.
     * If the data is null, this method returns null.
     *
     * @return a byte array representation of the string data, or null if the data is null.
     */
    public byte [] convertDataToByteArray() {
        if (data == null) {
            return null;
        }
        return data.getBytes();
    }

    /**
     * Creates a deep copy of this `Data` object.
     *
     * @return a clone of this `Data` object.
     */
    @Override
    public Data clone() {
        try {
            return (Data) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloning is supported but failed", e);
        }
    }
}

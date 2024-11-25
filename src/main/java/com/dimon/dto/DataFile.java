package com.dimon.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

/**
 * Represents a file with its name and content, designed for serialization and cloning in network protocols.
 * This class encapsulates the file's name, content, and the length of the file name.
 */
@Getter
@Setter
public class DataFile {
    /**
     * The name of the file.
     */
    private String fileName;

    /**
     * The content of the file represented as a byte array.
     */
    private byte[] fileContent;

    /**
     * The length of the file name, used for metadata purposes.
     */
    private int nameLength;

    /**
     * Constructs a new `DataFile` object with the specified file name, content, and name length.
     *
     * @param fileName the name of the file.
     * @param fileContent the content of the file as a byte array.
     * @param nameLength the length of the file name.
     */
    public DataFile(String fileName, byte[] fileContent, int nameLength) {
        this.fileName = fileName;
        this.fileContent = fileContent;
        this.nameLength = nameLength;
    }

    /**
     * Copy constructor to create a deep copy of another `DataFile` object.
     *
     * @param other the `DataFile` object to copy.
     */
    public DataFile(DataFile other) {
        this.fileName = other.fileName;
        this.fileContent = other.fileContent != null ? Arrays.copyOf(other.fileContent, other.fileContent.length) : null;
        this.nameLength = other.getNameLength();
    }

    /**
     * Converts the file's name and content into a single byte array for serialization.
     * The file name is serialized first, followed by the file content.
     *
     * @return a byte array containing the serialized file name and content.
     */
    public byte [] toByteArray() {
        byte [] nameBytes = fileName.getBytes();
        byte[] data = new byte[nameBytes.length + fileContent.length];

        int index = 0;

        System.arraycopy(nameBytes, 0, data, index, nameBytes.length);
        index += nameBytes.length;

        System.arraycopy(fileContent, 0, data, index, fileContent.length);

        return data;
    }

    /**
     * Returns a string representation of the `DataFile` object, including its file name
     * and the length of the file content.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "DataFile{" +
                "fileName='" + fileName + '\'' +
                ", fileContent (length)=" + fileContent.length +
                '}';
    }
}

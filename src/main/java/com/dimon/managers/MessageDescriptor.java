package com.dimon.managers;

import lombok.Getter;
import lombok.Setter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Manages the assembly and storage of message fragments for a UDP protocol.
 * Uses an in-memory buffer with a configurable memory limit, automatically
 * writing to a temporary file when the memory limit is exceeded.
 */
@Getter
@Setter
public class MessageDescriptor {

    /**
     * Maximum size (in bytes) of the in-memory buffer before flushing data to disk.
     */
    public final int MEMORY_LIMIT = 1024 * 1024;

    /**
     * In-memory buffer for message fragments.
     */
    private StringBuilder messageBuilder = new StringBuilder();

    /**
     * Temporary file used for storing message fragments when the memory limit is exceeded.
     */
    private File tempMessageFile;

    /**
     * Buffered writer for writing to the temporary file.
     */
    private BufferedWriter writer;

    /**
     * Initializes the temporary file and its writer if not already initialized.
     *
     * @throws IOException if an error occurs while creating the temporary file or writer.
     */
    private void initializeTempMessageFile() throws IOException {
        if (tempMessageFile == null) {
            tempMessageFile = File.createTempFile("message_", ".txt");
            writer = new BufferedWriter(new FileWriter(tempMessageFile, true));
        }
    }

    /**
     * Appends a message fragment to the in-memory buffer. If adding the fragment
     * exceeds the memory limit, the buffer is flushed to the temporary file.
     *
     * @param fragment the message fragment to append.
     * @throws IOException if an error occurs while flushing the buffer to disk.
     */
    public void appendFragmentToMessage(String fragment) throws IOException {
        if (messageBuilder.length() + fragment.length() > MEMORY_LIMIT) {
            flushMessageToDisk();
        }
        System.out.println("Adding fragment to messageBuilder: " + fragment);
        messageBuilder.append(fragment);
    }

    /**
     * Flushes the in-memory buffer to the temporary file. This method is called
     * automatically when the memory limit is exceeded.
     *
     * @throws IOException if an error occurs while writing to the temporary file.
     */
    private void flushMessageToDisk() throws IOException {
        if (!messageBuilder.isEmpty()) {
            initializeTempMessageFile();
            writer.write(messageBuilder.toString());
            writer.flush();
            messageBuilder.setLength(0); // Clear the in-memory buffer
        }
    }

    /**
     * Retrieves the complete message by combining the content of the temporary file
     * and any remaining data in the in-memory buffer. Cleans up resources after reading.
     *
     * @return the complete message as a single string.
     * @throws IOException if an error occurs while reading the temporary file.
     */
    public String getCompleteMessage() throws IOException {
        flushMessageToDisk();

        StringBuilder completeMessage = new StringBuilder();
        if (tempMessageFile != null && tempMessageFile.exists()) {
            completeMessage.append(Files.readString(tempMessageFile.toPath()));
        }

        completeMessage.append(messageBuilder);

        closeMessageFile();

        return completeMessage.toString();
    }

    /**
     * Closes the writer and cleans up the temporary file.
     *
     * @throws IOException if an error occurs while closing the writer.
     */
    public void closeMessageFile() throws IOException {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                System.err.println("Error closing writer: " + e.getMessage());
            }
        }
        if (tempMessageFile != null && tempMessageFile.exists()) {
            tempMessageFile.deleteOnExit();
            tempMessageFile = null; // Ensure it is not reused
        }
    }
}

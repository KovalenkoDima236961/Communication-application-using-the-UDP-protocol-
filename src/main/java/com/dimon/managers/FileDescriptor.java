package com.dimon.managers;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.io.*;

/**
 * Manages file-related operations for temporary and final file handling in the protocol.
 * Provides methods to initialize temporary files, append fragments, finalize file transfers,
 * and reset the file descriptor state.
 */
@Getter
@Setter
public class FileDescriptor {

    /**
     * The temporary file used for assembling file fragments.
     */
    public RandomAccessFile tempFile;

    /**
     * The physical temporary file object.
     */
    public File tempFileo;

    /**
     * The current position in the file where the next fragment will be written.
     */
    public long filePosition = 0;

    /**
     * The current position in the file name processing.
     */
    public int fileNamePosition = 0;

    /**
     * The current position in the file content processing.
     */
    public int fileContentPosition = 0;

    /**
     * The default folder where files will be saved.
     */
    public String destinationFolder = "C:\\Download";

    /**
     * Reads the content of a file and converts it into a byte array.
     *
     * @param file the file to be read.
     * @return a byte array containing the file's content.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public byte [] readFileToBytes(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte [] fileBytes = new byte[(int) file.length()];
            fileInputStream.read(fileBytes);
            return fileBytes;
        }
    }

    /**
     * Initializes a temporary file in the destination folder with the specified name.
     * If the temporary file already exists, it will be deleted.
     *
     * @param fileName the name of the temporary file to be created.
     * @throws IOException if the temporary file cannot be created or deleted.
     */
    public void initializeTemporaryFile(String fileName) throws IOException {
        if (tempFile != null) {
            throw new IllegalStateException("Temporary file is already initialized. Please reset before reinitializing.");
        }

        File directory = new File(destinationFolder);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create destination folder: " + destinationFolder);
        }

        tempFileo = new File(directory, fileName + ".tmp");
        if (tempFileo.exists() && !tempFileo.delete()) {
            throw new IOException("Failed to delete existing temporary file: " + tempFileo.getAbsolutePath());
        }

        tempFile = new RandomAccessFile(tempFileo, "rw");
        System.out.println("Temporary file initialized at: " + tempFileo.getAbsolutePath());
    }

    /**
     * Appends a fragment of data to the temporary file at the current position.
     *
     * @param fragment the data fragment to append.
     * @throws IOException if the temporary file is not initialized or the fragment is invalid.
     */
    public void appendFragmentToFile(byte[] fragment) throws IOException {
        if (tempFile == null) {
            throw new IOException("Temporary file is not initialized or has been closed.");
        }

        if (fragment == null || fragment.length == 0) {
            throw new IOException("Invalid fragment: fragment is null or empty.");
        }

        tempFile.seek(filePosition);
        tempFile.write(fragment);
        filePosition += fragment.length;
    }

    /**
     * Finalizes the file transfer by closing the temporary file, renaming it to the final destination,
     * and optionally opening the final file in the system's default application.
     *
     * @param destinationPath the path where the final file will be saved.
     * @throws IOException if the file cannot be closed, renamed, or opened.
     */
    public void finalizeFileTransfer(String destinationPath) throws IOException {
        if (tempFile != null) {
            tempFile.close();
        }

        File finalFile = new File(destinationPath);

        if (finalFile.exists() && !finalFile.delete()) {
            throw new IOException("Failed to delete existing file at destination: " + finalFile.getAbsolutePath());
        }

        if (!tempFileo.renameTo(finalFile)) {
            throw new IOException("Failed to rename temporary file to final destination. Temp: " +
                    tempFileo.getAbsolutePath() + ", Final: " + finalFile.getAbsolutePath());
        }

        System.out.println("File transfer completed and saved to: " + finalFile.getAbsolutePath());

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(finalFile);
                System.out.println("File opened successfully.");
            } catch (IOException e) {
                System.err.println("Error opening the file: " + e.getMessage());
            }
        }
    }

    /**
     * Resets the state of the file descriptor by closing and removing the temporary file,
     * and resetting all positions and references.
     */
    public void reset() {
        try {
            if (tempFile != null) {
                tempFile.close();
                System.out.println("Temporary file closed.");
            }
        } catch (IOException e) {
            System.err.println("Error closing temporary file: " + e.getMessage());
        } finally {
            tempFile = null;
            tempFileo = null;
            filePosition = 0;
            fileNamePosition = 0;
            fileContentPosition = 0;
        }
    }

    /**
     * Deletes the temporary file if it exists.
     */
    public void deleteTemporaryFile() {
        if (tempFileo != null && tempFileo.exists()) {
            if (tempFileo.delete()) {
                System.out.println("Temporary file deleted: " + tempFileo.getAbsolutePath());
            } else {
                System.err.println("Failed to delete temporary file: " + tempFileo.getAbsolutePath());
            }
        }
    }
}

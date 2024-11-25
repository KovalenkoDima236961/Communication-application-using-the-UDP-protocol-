package com.dimon.managers;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the file-related state and directory settings for the protocol.
 * This class provides mechanisms to track if a file operation is pending,
 * manage a user-specified directory, and handle directory input states.
 */
@Getter
@Setter
public class FileManager {

    /**
     * Indicates whether a file operation (e.g., saving a file) is currently waiting to be processed.
     */
    private boolean isFileWaiting = false;

    /**
     * Stores the user-specified directory path for saving files.
     * Defaults to an empty string if no directory is specified.
     */
    private String changedDir = "";

    /**
     * Tracks whether directory input from the user is pending.
     * Uses an {@link AtomicBoolean} to ensure thread-safe updates and checks.
     */
    private AtomicBoolean isDirectoryInputPending = new AtomicBoolean(false);
}

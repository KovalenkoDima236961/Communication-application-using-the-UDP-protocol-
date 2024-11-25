package com.dimon.utils;

/**
 * This interface defines the key constants used throughout the application.
 * These constants include configurations for fragment size, timeouts, keep-alive intervals,
 * and other protocol-related thresholds.
 */
public interface Constants {

     /**
      * The maximum size (in bytes) of a data fragment that can be sent in a single UDP packet.
      * This value is set to ensure compatibility with the typical MTU (Maximum Transmission Unit)
      * for network packets, while leaving space for headers.
      */
     int MAX_SIZE_FRAGMENT = 1458;

     /**
      * The interval (in milliseconds) between keep-alive messages sent to the peer
      * to ensure the connection remains active. If no activity is detected during this period,
      * a keep-alive message is sent.
      */
     int KEEP_ALIVE_INTERVAL = 5000;

     /**
      * The threshold (in milliseconds) after which a connection is considered timed out
      * if no activity or acknowledgment is received from the peer.
      */
     int TIMEOUT_THRESHOLD = 15000;

     /**
      * The maximum number of consecutive keep-alive message failures before the connection
      * is considered lost and terminated.
      */
     int HEARTBEAT_FAILURE_THRESHOLD = 3;
}
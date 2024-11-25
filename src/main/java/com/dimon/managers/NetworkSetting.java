package com.dimon.managers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Handles network configurations for UDP communication, including local and peer settings.
 * Provides methods to initialize sockets and resolve peer addresses.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NetworkSetting {

    /**
     * The local port number for binding the DatagramSocket.
     */
    private int localPort;

    /**
     * The IP address of the peer for UDP communication.
     */
    private String peerIp;

    /**
     * The port number of the peer for UDP communication.
     */
    private int peerPort;

    /**
     * The resolved `InetAddress` object of the peer.
     */
    private InetAddress peerAddress;

    /**
     * The `DatagramSocket` used for UDP communication.
     */
    private DatagramSocket socket;

    /**
     * Initializes the DatagramSocket on the specified local port.
     * If the socket is already initialized and open, this method does nothing.
     *
     * @throws IOException if an error occurs while creating the socket.
     */
    public void initializeSocket() throws IOException {
        if (socket == null || socket.isClosed()) {
            this.socket = new DatagramSocket(localPort);
            System.out.println("Socket initialized on port: " + localPort);
        }
    }

    /**
     * Closes the DatagramSocket if it is open.
     * Logs a message indicating that the socket has been closed.
     */
    public void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("Socket closed.");
        }
    }

    /**
     * Resolves the peer IP address into an `InetAddress` object.
     * Logs the resolved address for confirmation.
     *
     * @throws IOException if the peer IP cannot be resolved.
     */
    public void initializePeerAddress() throws IOException {
        if (peerIp != null && !peerIp.isEmpty()) {
            this.peerAddress = InetAddress.getByName(peerIp);
            System.out.println("Peer address resolved: " + peerAddress);
        }
    }
}

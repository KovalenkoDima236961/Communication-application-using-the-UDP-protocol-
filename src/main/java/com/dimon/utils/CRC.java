package com.dimon.utils;

import com.dimon.protocol.MyUDPProtocol;
import com.dimon.protocol.ProtocolForCRC;
import com.dimon.protocol.ProtocolForCRCWithoutData;

import java.util.zip.CRC32;

/**
 * Utility class for calculating and verifying CRC (Cyclic Redundancy Check) values.
 * This class provides methods to compute CRC values for different protocol objects
 * and verify the integrity of data using CRC checksums.
 */
public class CRC {

    /**
     * Calculates the CRC32 checksum for a protocol object that contains data.
     *
     * @param myUDPProtocol the protocol object containing data for which the CRC is calculated.
     * @return the computed CRC32 value as an integer.
     */
    public static int calculateCRC(ProtocolForCRC myUDPProtocol) {
        byte [] byteData = myUDPProtocol.toByteArray();
        CRC32 crc32 = new CRC32();
        crc32.update(byteData);

        return (int) crc32.getValue();
    }

    /**
     * Calculates the CRC32 checksum for a protocol object that does not contain data.
     *
     * @param myUDPProtocol the protocol object without data for which the CRC is calculated.
     * @return the computed CRC32 value as an integer.
     */
    public static int calculateCRC(ProtocolForCRCWithoutData
                                           myUDPProtocol) {
        byte [] byteData = myUDPProtocol.toByteArray();
        CRC32 crc32 = new CRC32();
        crc32.update(byteData);

        return (int) crc32.getValue();
    }

    /**
     * Verifies the CRC32 checksum for a protocol object without data.
     *
     * @param myUDPProtocol the protocol object without data for which the CRC is being verified.
     * @param checksum the expected CRC32 value to compare against.
     * @return true if the computed CRC matches the expected checksum, false otherwise.
     */
    public static boolean checkCRC(ProtocolForCRCWithoutData myUDPProtocol, int checksum) {
        byte[] byteData = myUDPProtocol.toByteArray();

        CRC32 crc32 = new CRC32();
        crc32.update(byteData);
        int check = (int) crc32.getValue();

        return checksum == check;
    }

    /**
     * Verifies the CRC32 checksum for a protocol object containing data.
     *
     * @param myUDPProtocol the protocol object with data for which the CRC is being verified.
     * @param checksum the expected CRC32 value to compare against.
     * @return true if the computed CRC matches the expected checksum, false otherwise.
     */
    public static boolean checkCRC(ProtocolForCRC myUDPProtocol, int checksum) {
        byte[] byteData = myUDPProtocol.toByteArray();

        CRC32 crc32 = new CRC32();
        crc32.update(byteData);
        int check = (int) crc32.getValue();

        return checksum == check;
    }

    /**
     * Validates the CRC32 checksum for a given protocol object, determining
     * whether it contains data or not and using the appropriate method.
     *
     * @param protocol the protocol object for which the CRC is being validated.
     * @return true if the computed CRC matches the stored checksum, false otherwise.
     */
    public static boolean isValidCRC(MyUDPProtocol protocol) {
        int checksum = Utils.byteArrayToInt(protocol.getCheckSumCRC());
        if (protocol.getData() != null || protocol.getDataFile() != null) {
            return checkCRC(new ProtocolForCRC(protocol), checksum);
        } else {
            return checkCRC(new ProtocolForCRCWithoutData(protocol), checksum);
        }
    }
}
package com.dimon.managers;

import com.dimon.protocol.MyUDPProtocol;
import com.dimon.utils.Utils;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state and operations of UDP fragments during transmission.
 * This class tracks pending fragments, acknowledgments, sequence numbers,
 * and other metadata required for reliable fragment management in the protocol.
 */
@Getter
@Setter
public class FragmentManager {

    /**
     * Queue of fragments that are pending acknowledgment or retransmission.
     */
    private Queue<MyUDPProtocol> pendingFragments = new LinkedList<>();

    /**
     * The last sent or received protocol fragment for reference.
     */
    private MyUDPProtocol finalRequest;

    /**
     * The total number of fragments in the current transmission.
     */
    private int howMany;

    /**
     * The sequence number counter, incremented for each new fragment.
     */
    private int seqNumberCounter;

    /**
     * The counter for the current window of fragments being sent.
     */
    private int windowCounter;

    /**
     * The maximum number of fragments that can be sent in a single window.
     */
    private int windowSize = 4;

    /**
     * Flag indicating whether the first packet should be sent corrupted (used for testing).
     */
    private boolean sendCorruptedFirstPacket = false;

    /**
     * The minimum length of any fragment sent during the transmission.
     */
    private int minLengthOfFragment = Integer.MAX_VALUE;

    /**
     * Map of sequence numbers to their corresponding send timestamps for timeout tracking.
     */
    private Map<Integer, Long> sendTimestamps = new ConcurrentHashMap<>();

    /**
     * Flag indicating whether the header fragment has been sent.
     */
    private boolean isHeaderSent = false;

    /**
     * Flag indicating whether the transmission has reached its end.
     */
    private boolean end = false;

    /**
     * Resets the state of the fragment manager, clearing all tracked data
     * and resetting counters and flags to their initial values.
     */
    public void reset() {
        finalRequest = null;
        pendingFragments.clear();
        seqNumberCounter = 0;
        windowCounter = 0;
        windowSize = 4;
        isHeaderSent = false;
        end = false;
        sendCorruptedFirstPacket = false;
        minLengthOfFragment = Integer.MAX_VALUE;
        sendTimestamps.clear();
    }

    /**
     * Removes a fragment from the pending queue based on its sequence number.
     *
     * @param seqNumber the sequence number of the fragment to be removed.
     */
    public void clearPendingFragments(int seqNumber) {
        boolean removed = pendingFragments.removeIf(fragment ->
                Utils.byteArrayToInt(fragment.getSequenceNumber()) == seqNumber
        );
        if (removed) {
            System.out.println("Removed fragment with SeqNumber: " + seqNumber);
        }
    }

    /**
     * Adds a fragment to the pending queue and records its send timestamp.
     *
     * @param fragment the fragment to be added.
     * @param sendTime the timestamp when the fragment was sent.
     */
    public void addFragment(MyUDPProtocol fragment, long sendTime) {
        pendingFragments.add(fragment);
        sendTimestamps.put(seqNumberCounter, sendTime);
        updateMinFragmentSize(fragment.toByteArrayWithData().length);
        incrementCounters();
    }

    /**
     * Updates the minimum fragment size if the provided fragment is smaller.
     *
     * @param fragmentLength the length of the fragment to compare.
     */
    public void updateMinFragmentSize(int fragmentLength) {
        minLengthOfFragment = Math.min(minLengthOfFragment, fragmentLength);
    }

    /**
     * Increments the sequence number and window counters.
     */
    private void incrementCounters() {
        seqNumberCounter++;
        windowCounter++;
    }

    /**
     * Decrements the window counter to reflect a processed fragment.
     */
    public void decrementWindowCounter() {
        windowCounter--;
    }

    /**
     * Checks whether more fragments can be sent based on the current window size.
     *
     * @return true if more fragments can be sent; false otherwise.
     */
    public boolean canSendMoreFragments() {
        return windowCounter < windowSize;
    }

    /**
     * Marks the header as sent and logs the action.
     */
    public void markHeaderAsSent() {
        if (!isHeaderSent) {
            isHeaderSent = true;
        }
    }
}

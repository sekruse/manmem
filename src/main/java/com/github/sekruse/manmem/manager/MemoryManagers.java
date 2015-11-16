package com.github.sekruse.manmem.manager;

/**
 * Helper methods for the work with {@link MemoryManager}s.
 */
public class MemoryManagers {

    /**
     * Calculate the number of required segments to host a given amount of memory.
     *
     * @param memory      the memory to be hosted
     * @param segmentSize the size of segments (in bytes)
     * @return the number of segments to host the memory
     */
    public static int requiredSegments(long memory, int segmentSize) {
        return (int) ((memory + segmentSize - 1) / segmentSize);
    }

    /**
     * Calculate the amount of memory provided by a number of segments
     *
     * @param numSegments the number of segments
     * @param segmentSize the size of the segments (in bytes)
     * @return the provided memory (in bytes)
     */
    public static long providedMemory(int numSegments, int segmentSize) {
        return numSegments * (long) segmentSize;
    }

    /**
     * Enlarges the given memory so that fits exactly into segments of a given size.
     *
     * @param memory      the amount of memory to enlarge (in bytes)
     * @param segmentSize the size of segments (in bytes)
     * @return
     */
    public static long fitMemoryToSegments(long memory, int segmentSize) {
        return providedMemory(requiredSegments(memory, segmentSize), segmentSize);
    }

}

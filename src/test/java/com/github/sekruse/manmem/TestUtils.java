package com.github.sekruse.manmem;

import com.github.sekruse.manmem.io.DiskOperator;
import org.slf4j.LoggerFactory;

/**
 * Utilities for testing.
 */
public class TestUtils {

    private static long resetTime = System.currentTimeMillis();

    /**
     * Resets static statistics pertaining to this project.
     */
    public static void resetIoStats() {
        DiskOperator.SEGMENT_LOADS.set(0);
        DiskOperator.SEGMENT_WRITES.set(0);
        resetTime = System.currentTimeMillis();
    }

    /**
     * Logs statistics pertaining to this project.
     *
     * @param caller the object calling this function
     * @param methodSignature the signature of the test method
     */
    public static void logIoStats(Object caller, String methodSignature) {
        final long numSegmentLoads = DiskOperator.SEGMENT_LOADS.get();
        final long numSegmentWrites = DiskOperator.SEGMENT_WRITES.get();
        final long elapsedTime = System.currentTimeMillis() - resetTime;
        LoggerFactory
                .getLogger(caller.getClass())
                .info("{}: {} segment loads / {} segment writes in the last {} ms",
                        methodSignature, numSegmentLoads, numSegmentWrites, elapsedTime);
    }
}

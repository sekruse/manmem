package com.github.sekruse.manmem.collection;

import com.github.sekruse.manmem.TestUtils;
import com.github.sekruse.manmem.collection.list.IntArray;
import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.GlobalMemoryManager;
import com.github.sekruse.manmem.manager.MemoryManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Random;

/**
 * Test suite for {@link com.github.sekruse.manmem.collection.list.IntArray}s.
 */
public class IntArrayTest {

    private static final int KB = 1024;

    private static final int MB = 1024 * KB;

    @Test
    public void testSimpleSetAndGet() {
        TestUtils.resetIoStats();

        final int numIntegers = 10 * MB / Integer.BYTES;

        // Generate some test data.
        final int[] testData = new int[numIntegers];
        final Random random = new Random(42);
        for (int i = 0; i < testData.length; i++) {
            testData[i] = random.nextInt();
        }

        // Create an IntArray.
        MemoryManager memoryManager = new GlobalMemoryManager(10 * MB, 32 * KB);
        final IntArray intArray = new IntArray(testData.length, memoryManager);

        // Insert the test data in some random order.
        copyInRandomOrder(testData, random, intArray);

        // Read and check the data in some random order.
        compareInRandomOrder(testData, random, intArray);

        memoryManager.close();

        TestUtils.logIoStats(this, "testSimpleSetAndGet()");
    }

    @Test
    public void testSimpleSetAndGetWithWriteLock() {
        TestUtils.resetIoStats();

        final int numIntegers = 10 * MB / Integer.BYTES;

        // Generate some test data.
        final int[] testData = new int[numIntegers];
        final Random random = new Random(42);
        for (int i = 0; i < testData.length; i++) {
            testData[i] = random.nextInt();
        }

        // Create an IntArray.
        MemoryManager memoryManager = new GlobalMemoryManager(10 * MB, 32 * KB);
        final IntArray intArray = new IntArray(testData.length, memoryManager);
        intArray.lockForWrite();

        // Insert the test data in some random order.
        copyInRandomOrder(testData, random, intArray);

        // Read and check the data in some random order.
        compareInRandomOrder(testData, random, intArray);

        memoryManager.close();

        TestUtils.logIoStats(this, "testSimpleSetAndGetWithWriteLock()");
    }

    @Test
    public void testSimpleSetAndGetWithWriteAndReadLocks() {
        TestUtils.resetIoStats();

        final int numIntegers = 10 * MB / Integer.BYTES;

        // Generate some test data.
        final int[] testData = new int[numIntegers];
        final Random random = new Random(42);
        for (int i = 0; i < testData.length; i++) {
            testData[i] = random.nextInt();
        }

        // Create an IntArray.
        MemoryManager memoryManager = new GlobalMemoryManager(10 * MB, 32 * KB);
        final IntArray intArray = new IntArray(testData.length, memoryManager);
        intArray.lockForWrite();

        // Insert the test data in some random order.
        copyInRandomOrder(testData, random, intArray);

        intArray.unlock();
        intArray.lockForRead();

        // Read and check the data in some random order.
        compareInRandomOrder(testData, random, intArray);

        intArray.unlock();

        memoryManager.close();

        TestUtils.logIoStats(this, "testSimpleSetAndGetWithWriteAndReadLocks()");
    }

    /**
     * Compares {@code testData} in a random order (controlled by {@code random}) to {@code intArray}.
     *
     * @param testData the ground truth data structure
     * @param random   controls the randomness
     * @param intArray the data structure to verify
     */
    public void compareInRandomOrder(int[] testData, Random random, IntArray intArray) {
        final IntList order = generateRandomPermutation(testData.length, random);
        for (IntIterator iterator = order.iterator(); iterator.hasNext(); ) {
            int next = iterator.nextInt();
            Assert.assertEquals(testData[next], intArray.get(next));
        }
    }

    /**
     * Copies {@code testData} in a random order (controlled by {@code random}) into {@code intArray}.
     *
     * @param testData the source data structure
     * @param random   controls the randomness
     * @param intArray the target data structure
     */
    public void copyInRandomOrder(int[] testData, Random random, IntArray intArray) {
        final IntList order = generateRandomPermutation(testData.length, random);
        for (IntIterator iterator = order.iterator(); iterator.hasNext(); ) {
            int next = iterator.nextInt();
            intArray.set(next, testData[next]);
        }
    }

    /**
     * Generate a permutation of values starting from {@code 0}.
     *
     * @param numValues the (exclusive) maximum value; i.e., number of values in the permutation
     * @param random    a {@link Random} to provide randomness
     */
    public IntList generateRandomPermutation(int numValues, Random random) {
        // Write the data in some arbitrary order.
        IntList order = new IntArrayList(numValues);
        for (int i = 0; i < numValues; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        return order;
    }

    @Test(expected = IllegalStateException.class)
    public void testWritingOnReadLockedIntArrayFails() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            IntArray intArray = new IntArray(100, memoryManager);
            intArray.lockForRead();
            intArray.set(50, 10);
        } finally {
            memoryManager.close();
        }
    }

    @Test
    public void testAcquiringReadLockAlwaysWorks() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            IntArray intArray = new IntArray(100, memoryManager);
            intArray.lockForWrite();
            intArray.lockForRead();
            intArray.unlock();
            intArray.lockForRead();
            intArray.lockForRead();
        } finally {
            memoryManager.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testAcquiringWriteLockOnReadLockedIntArrayFails() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            IntArray intArray = new IntArray(100, memoryManager);
            intArray.lockForRead();
            intArray.lockForWrite();
        } finally {
            memoryManager.close();
        }
    }

    @Test
    public void testAcquiringWriteLockOnWriteLockedIntArrayWorks() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            IntArray intArray = new IntArray(100, memoryManager);
            intArray.lockForWrite();
            intArray.lockForWrite();
        } finally {
            memoryManager.close();
        }
    }

    @Test
    public void testRelocking() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            IntArray intArray = new IntArray(100, memoryManager);
            intArray.lockForRead();
            intArray.unlock();
            intArray.lockForWrite();
        } finally {
            memoryManager.close();
        }
    }

    @Test(expected = CapacityExceededException.class)
    public void testLockingCanBreakCapacities() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            IntArray intArray = new IntArray(512, memoryManager);
            intArray.lockForRead();
        } finally {
            memoryManager.close();
        }
    }


}

package com.github.sekruse.manmem.collection;

import com.github.sekruse.manmem.TestUtils;
import com.github.sekruse.manmem.collection.list.CuckooHashTable;
import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.GlobalMemoryManager;
import com.github.sekruse.manmem.manager.MemoryManager;
import it.unimi.dsi.fastutil.ints.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Random;

/**
 * Test suite for {@link com.github.sekruse.manmem.collection.list.CuckooHashTable}s.
 */
public class CuckooHashTableTest {

    private static final int KB = 1024;

    private static final int MB = 1024 * KB;

    @Test
    public void testSimplePutAndGet() {
        TestUtils.resetIoStats();

        final int numEntries = 10 * MB / Integer.BYTES / 2 / 2;
        final int noKey = -1;

        // Generate some test data.
        final Int2IntMap testData = new Int2IntOpenHashMap(numEntries);
        final Random random = new Random(42);
        while (testData.size() < numEntries) {
            int key;
            do {
                key = random.nextInt();
            } while (key == noKey);
            testData.put(key, random.nextInt());
        }

        // Create an CuckooHashTable.
        final int segmentSize = 32 * KB;
        final int numNeededSegments = CuckooHashTable.estimateNumSegments(numEntries, 0.5, segmentSize);
        MemoryManager memoryManager = new GlobalMemoryManager(numNeededSegments * segmentSize, segmentSize);
        CuckooHashTable hashTable = new CuckooHashTable(numEntries * 2,
                memoryManager,
                noKey,
                new JenkinsHashFunction.Factory(),
                random);

        // Insert the test data in some random order.
        copyInRandomOrder(testData, random, hashTable);

        // Read and check the data in some random order.
        compareInRandomOrder(testData, random, hashTable);

        memoryManager.close();

        TestUtils.logIoStats(this, "testSimplePutAndGet()");
    }

    @Test
    public void testSimplePutAndGetWithWriteLock() {
        TestUtils.resetIoStats();

        final int numEntries = 10 * MB / Integer.BYTES / 2 / 2;
        final int noKey = -1;

        // Generate some test data.
        final Int2IntMap testData = new Int2IntOpenHashMap(numEntries);
        final Random random = new Random(42);
        while (testData.size() < numEntries) {
            int key;
            do {
                key = random.nextInt();
            } while (key == noKey);
            testData.put(key, random.nextInt());
        }

        // Create an CuckooHashTable.
        final int segmentSize = 32 * KB;
        final int numNeededSegments = CuckooHashTable.estimateNumSegments(numEntries, 0.5, segmentSize);
        MemoryManager memoryManager = new GlobalMemoryManager(numNeededSegments * segmentSize, segmentSize);
        CuckooHashTable hashTable = new CuckooHashTable(numEntries * 2,
                memoryManager,
                noKey,
                new JenkinsHashFunction.Factory(),
                random);
        hashTable.lockForWrite();

        // Insert the test data in some random order.
        copyInRandomOrder(testData, random, hashTable);

        // Read and check the data in some random order.
        compareInRandomOrder(testData, random, hashTable);

        hashTable.unlock();
        memoryManager.close();

        TestUtils.logIoStats(this, "testSimplePutAndGetWithWriteLock()");

    }

    @Test
    public void testSimplePutAndGetWithWriteAndReadLocks() {
        TestUtils.resetIoStats();

        final int numEntries = 10 * MB / Integer.BYTES / 2 / 2;
        final int noKey = -1;

        // Generate some test data.
        final Int2IntMap testData = new Int2IntOpenHashMap(numEntries);
        final Random random = new Random(42);
        while (testData.size() < numEntries) {
            int key;
            do {
                key = random.nextInt();
            } while (key == noKey);
            testData.put(key, random.nextInt());
        }

        // Create an CuckooHashTable.
        final int segmentSize = 32 * KB;
        final int numNeededSegments = CuckooHashTable.estimateNumSegments(numEntries, 0.5, segmentSize);
        MemoryManager memoryManager = new GlobalMemoryManager(numNeededSegments * segmentSize, segmentSize);
        CuckooHashTable hashTable = new CuckooHashTable(numEntries * 2,
                memoryManager,
                noKey,
                new JenkinsHashFunction.Factory(),
                random);

        // Insert the test data in some random order.
        hashTable.lockForWrite();
        copyInRandomOrder(testData, random, hashTable);
        hashTable.unlock();

        // Read and check the data in some random order.
        hashTable.lockForRead();
        compareInRandomOrder(testData, random, hashTable);
        hashTable.unlock();

        memoryManager.close();

        TestUtils.logIoStats(this, "testSimplePutAndGetWithWriteAndReadLocks()");

    }

    /**
     * Compares {@code testData} in a random order (controlled by {@code random}) to {@code hashTable}.
     *
     * @param testData  the ground truth data structure
     * @param random    controls the randomness
     * @param hashTable the data structure to verify
     */
    public void compareInRandomOrder(Int2IntMap testData, Random random, CuckooHashTable hashTable) {
        final IntList keys = generateRandomKeyPermutation(testData, random);
        for (IntIterator iterator = keys.iterator(); iterator.hasNext(); ) {
            int key = iterator.nextInt();
            Assert.assertEquals(testData.get(key), hashTable.get(key, -1));
        }
    }

    /**
     * Copies {@code testData} in a random order (controlled by {@code random}) into {@code hashTable}.
     *
     * @param testData  the source data structure
     * @param random    controls the randomness
     * @param hashTable the target data structure
     */
    public void copyInRandomOrder(Int2IntMap testData, Random random, CuckooHashTable hashTable) {
        final IntList keys = generateRandomKeyPermutation(testData, random);
        for (IntIterator iterator = keys.iterator(); iterator.hasNext(); ) {
            int key = iterator.nextInt();
            hashTable.put(key, testData.get(key));
        }
    }

    /**
     * Generate a permutation of values starting from {@code 0}.
     *
     * @param numValues the (exclusive) maximum value; i.e., number of values in the permutation
     * @param random    a {@link Random} to provide randomness
     */
    public IntList generateRandomKeyPermutation(Int2IntMap numValues, Random random) {
        // Write the data in some arbitrary order.
        IntList keys = new IntArrayList(numValues.keySet());
        Collections.shuffle(keys, random);
        return keys;
    }

    @Test(expected = IllegalStateException.class)
    public void testWritingOnReadLockedCuckooHashTableFails() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            CuckooHashTable hashTable = new CuckooHashTable(10, memoryManager, -1, new JenkinsHashFunction.Factory(),
                    new Random(42));
            hashTable.lockForRead();
            hashTable.put(50, 10);
        } finally {
            memoryManager.close();
        }
    }

    @Test
    public void testAcquiringReadLockAlwaysWorks() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            CuckooHashTable hashTable = new CuckooHashTable(10, memoryManager, -1, new JenkinsHashFunction.Factory(),
                    new Random(42));
            hashTable.lockForWrite();
            hashTable.lockForRead();
            hashTable.unlock();
            hashTable.lockForRead();
            hashTable.lockForRead();
        } finally {
            memoryManager.close();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testAcquiringWriteLockOnReadLockedCuckooHashTableFails() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            CuckooHashTable hashTable = new CuckooHashTable(10, memoryManager, -1, new JenkinsHashFunction.Factory(),
                    new Random(42));
            hashTable.lockForRead();
            hashTable.lockForWrite();
        } finally {
            memoryManager.close();
        }
    }

    @Test
    public void testAcquiringWriteLockOnWriteLockedCuckooHashTableWorks() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            CuckooHashTable hashTable = new CuckooHashTable(10, memoryManager, -1, new JenkinsHashFunction.Factory(),
                    new Random(42));
            hashTable.lockForWrite();
            hashTable.lockForWrite();
        } finally {
            memoryManager.close();
        }
    }

    @Test
    public void testRelocking() {
        MemoryManager memoryManager = new GlobalMemoryManager(512, 32);
        try {
            CuckooHashTable hashTable = new CuckooHashTable(10, memoryManager, -1, new JenkinsHashFunction.Factory(),
                    new Random(42));
            hashTable.lockForRead();
            hashTable.unlock();
            hashTable.lockForWrite();
        } finally {
            memoryManager.close();
        }
    }

    @Test(expected = CapacityExceededException.class)
    public void testLockingCanBreakCapacities() {
        final int numRequiredSegments = CuckooHashTable.estimateNumSegments(10, 1, 32);
        MemoryManager memoryManager = new GlobalMemoryManager((numRequiredSegments - 1) * 32, 32);
        try {
            CuckooHashTable hashTable = new CuckooHashTable(10, memoryManager, -1, new JenkinsHashFunction.Factory(),
                    new Random(42));
            hashTable.lockForRead();
        } finally {
            memoryManager.close();
        }
    }


}

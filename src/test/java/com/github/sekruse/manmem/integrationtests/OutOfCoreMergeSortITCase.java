package com.github.sekruse.manmem.integrationtests;

import com.github.sekruse.manmem.manager.GlobalMemoryManager;
import com.github.sekruse.manmem.manager.MemoryManager;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;
import com.github.sekruse.manmem.memory.ReadAccess;
import com.github.sekruse.manmem.memory.WriteAccess;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Tests a multi-phase two-way merge sort on data that does not fit into main memory.
 */
public class OutOfCoreMergeSortITCase {

    private static final int KB = 1024;

    private static final int MB = 1024 * 1024;

    @Test
    public void test() {
        // Create 10 MB of random bytes.
        byte[] originalData = new byte[10 * MB];
        Random random = new Random(42);
        random.nextBytes(originalData);

        // Copy and sort the data.
        byte[] sortedData = originalData.clone();
        Arrays.sort(sortedData);

        // Create a GlobalMemoryManger with a main memory capacity of 1 MB.
        MemoryManager memoryManager = new GlobalMemoryManager(1 * MB, 32 * KB);

        // Load the original data into the managed memory.
        List<VirtualMemorySegment> memories = load(originalData, memoryManager);

        // Sort all the chunks individually.
        for (VirtualMemorySegment virtualMemorySegment : memories) {
            sort(virtualMemorySegment);
        }

        // Recursively merge all the segments.
        mergeSort(memories, 0, memories.size(), memoryManager);

        // Check if the memory chunks contain the same as originalData.
        compare(sortedData, memories);

        // Quit.
        memoryManager.close();
    }

    /**
     * Compares a given byte array with the content of a list of memory chunks for equality.
     *
     * @param byteArray a {@code byte[]}
     * @param memories  a list of {@link VirtualMemorySegment} objects
     */
    private void compare(byte[] byteArray, List<VirtualMemorySegment> memories) {
        Iterator<VirtualMemorySegment> memoryIterator = memories.iterator();
        int originalDataPos = 0;
        while (originalDataPos < byteArray.length && memoryIterator.hasNext()) {
            VirtualMemorySegment currentVms = memoryIterator.next();
            try (ReadAccess readAccess = currentVms.getReadAccess()) {
                while (readAccess.getPayload().hasRemaining()) {
                    Assert.assertTrue(originalDataPos < byteArray.length);
                    byte readByte = readAccess.getPayload().get();
                    Assert.assertEquals(byteArray[originalDataPos], readByte);
                    originalDataPos++;
                }
            }
        }
        Assert.assertFalse(memoryIterator.hasNext());
        Assert.assertEquals(byteArray.length, originalDataPos);
    }

    /**
     * Merge two contiguous memory chunks.
     *
     * @param memories      the memories to be merged
     * @param from          the first memory index of the first memory range
     * @param to            the first memory index after the second memory range
     * @param memoryManager manager of the memory
     */
    private static void mergeSort(List<VirtualMemorySegment> memories, int from, int to, MemoryManager memoryManager) {
        // Subdivide the sorting range into two halves.
        int middle = (to - from) / 2 + from;
        if (middle - from > 1) mergeSort(memories, from, middle, memoryManager);
        if (to - middle > 1) mergeSort(memories, middle, to, memoryManager);

        List<VirtualMemorySegment> mergedChunks = new LinkedList<>();
        int firstMemoryIndex = from - 1, secondMemoryIndex = middle - 1;
        ReadAccess firstChunkReadAccess = null, secondChunkReadAccess = null;
        WriteAccess mergedChunkWriteAccess = null;

        while (true) {
            // Ensure the first chunk.
            if (firstChunkReadAccess == null) {
                if (++firstMemoryIndex < middle) {
                    final VirtualMemorySegment virtualMemorySegment = memories.get(firstMemoryIndex);
                    firstChunkReadAccess = virtualMemorySegment.getReadAccess();
                } else {
                    break;
                }
            }

            // Ensure the second chunk.
            if (secondChunkReadAccess == null) {
                if (++secondMemoryIndex < to) {
                    final VirtualMemorySegment virtualMemorySegment = memories.get(secondMemoryIndex);
                    secondChunkReadAccess = virtualMemorySegment.getReadAccess();
                } else {
                    break;
                }
            }

            final ByteBuffer firstPayload = firstChunkReadAccess.getPayload();
            final ByteBuffer secondPayload = secondChunkReadAccess.getPayload();

            // Merge the two chunks.
            while (firstPayload.hasRemaining() && secondPayload.hasRemaining()) {
                final byte firstElement = firstPayload.get(firstPayload.position());
                final byte secondElement = secondPayload.get(secondPayload.position());

                // If applicable, add the first element to the merged memory.
                if (firstElement <= secondElement) {
                    mergedChunkWriteAccess = copyByte(firstPayload, mergedChunkWriteAccess, mergedChunks, memoryManager);
                }

                // If applicable, add the second element to the merged memory.
                if (firstElement >= secondElement) {
                    mergedChunkWriteAccess = copyByte(secondPayload, mergedChunkWriteAccess, mergedChunks, memoryManager);
                }
            }

            // Close the finished read chunks.
            if (!firstPayload.hasRemaining()) {
                firstChunkReadAccess.close();
                firstChunkReadAccess = null;
            }
            if (!secondPayload.hasRemaining()) {
                secondChunkReadAccess.close();
                secondChunkReadAccess = null;
            }

        }

        // Copy any remaining bytes.
        copyRemainder(firstChunkReadAccess, firstMemoryIndex, middle, memories, mergedChunkWriteAccess,
                mergedChunks, memoryManager);
        copyRemainder(secondChunkReadAccess, secondMemoryIndex, to, memories, mergedChunkWriteAccess,
                mergedChunks, memoryManager);

        // Sanity check: the source and target chunks should be equal in number.
        Assert.assertEquals(to - from, mergedChunks.size());

        // Replace the original chunks with the merged chunks.
        for (int i = 0; i < mergedChunks.size(); i++) {
            final VirtualMemorySegment originalVms = memories.get(from + i);
            originalVms.release();
            memories.set(from + i, mergedChunks.get(i));
        }
    }

    /**
     * Copies remaining memory chunks to target chunks.
     *
     * @param curChunkReadAccess     the currently read chunk
     * @param curMemoryIndex         the index of the currently read chunk
     * @param maxMemoryIndex         the maximum index of chunks to read (exclusive)
     * @param readMemories           all read chunks
     * @param mergedChunkWriteAccess the current target chunk
     * @param mergedChunks           collector for new created chunks
     * @param memoryManager          allocates new target chunks if necessary
     */
    private static void copyRemainder(ReadAccess curChunkReadAccess, int curMemoryIndex, int maxMemoryIndex,
                                      List<VirtualMemorySegment> readMemories, WriteAccess mergedChunkWriteAccess,
                                      List<VirtualMemorySegment> mergedChunks, MemoryManager memoryManager) {
        while (true) {
            // Ensure the chunk.
            if (curChunkReadAccess == null) {
                if (++curMemoryIndex < maxMemoryIndex) {
                    final VirtualMemorySegment virtualMemorySegment = readMemories.get(curMemoryIndex);
                    curChunkReadAccess = virtualMemorySegment.getReadAccess();
                } else {
                    break;
                }
            }
            final ByteBuffer firstPayload = curChunkReadAccess.getPayload();

            // Merge the two chunks.
            while (firstPayload.hasRemaining()) {
                mergedChunkWriteAccess = copyByte(firstPayload, mergedChunkWriteAccess, mergedChunks, memoryManager);
            }

            // Close the finished read chunks.
            if (!firstPayload.hasRemaining()) {
                curChunkReadAccess.close();
                curChunkReadAccess = null;
            }
        }
    }

    /**
     * Copies a byte from a source to a target memory chunk.
     *
     * @param source                 the source providing a byte
     * @param targetChunkWriteAccess write access to the target memory
     * @param targetChunks           collector of new allocated target memory
     * @param memoryManager          allocates new target memory if {@code targetChunkWriteAccess} is {@code null}
     * @return the input target memory writer or {@code null} if that became full
     */
    private static WriteAccess copyByte(ByteBuffer source, WriteAccess targetChunkWriteAccess, List<VirtualMemorySegment> targetChunks, MemoryManager memoryManager) {
        if (targetChunkWriteAccess == null) {
            final VirtualMemorySegment mergeVms = memoryManager.requestDefaultMemory();
            targetChunks.add(mergeVms);
            targetChunkWriteAccess = mergeVms.getWriteAccess();
            targetChunkWriteAccess.getPayload().clear();
        }
        targetChunkWriteAccess.getPayload().put(source.get());
        if (!targetChunkWriteAccess.getPayload().hasRemaining()) {
            targetChunkWriteAccess.close();
            targetChunkWriteAccess = null;
        }
        return targetChunkWriteAccess;
    }

    /**
     * Sorts the bytes within the given memory.
     *
     * @param virtualMemorySegment the memory to be sorted
     */
    private static void sort(VirtualMemorySegment virtualMemorySegment) {
        try (WriteAccess writeAccess = virtualMemorySegment.getWriteAccess()) {
            final ByteBuffer payload = writeAccess.getPayload();
            if (!payload.hasArray()) {
                throw new RuntimeException("Require array-backed buffers for sorting.");
            }
            Arrays.sort(payload.array(), 0, payload.limit());
        }
    }

    /**
     * Load bytes into managed memory.
     *
     * @param originalData  the bytes to load
     * @param memoryManager the manager for the managed memory
     * @return a list of memory created chunks in order of the original data
     */
    private static List<VirtualMemorySegment> load(byte[] originalData, MemoryManager memoryManager) {
        // Initialize lists for the memory chunks.
        List<VirtualMemorySegment> chunks = new LinkedList<>();

        // Allocate and chunks and copy the data chunk wise.
        int originalDataPos = 0;
        while (originalDataPos < originalData.length) {
            // Allocate memory.
            VirtualMemorySegment currentChunk = memoryManager.requestDefaultMemory();
            chunks.add(currentChunk);

            // Copy the data.
            try (WriteAccess writeAccess = currentChunk.getWriteAccess()) {
                final ByteBuffer payload = writeAccess.getPayload();
                payload.clear();
                int numBytesToCopyCurrently;
                while ((numBytesToCopyCurrently = Math.min(originalData.length - originalDataPos, payload.remaining())) > 0) {
                    payload.put(originalData, originalDataPos, numBytesToCopyCurrently);
                    originalDataPos += numBytesToCopyCurrently;
                }
                payload.flip();
            }
        }

        return chunks;
    }

}

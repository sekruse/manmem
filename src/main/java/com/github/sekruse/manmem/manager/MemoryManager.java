package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.memory.VirtualMemorySegment;

/**
 * Interace to a memory manager.
 */
public interface MemoryManager {

    /**
     * Request the default share of memory served by this memory manager.
     *
     * @return a {@link VirtualMemorySegment} representing the requested memory
     * @throws CapacityExceededException if the memory manager cannot serve the request due to missing capacities
     */
    VirtualMemorySegment requestDefaultMemory() throws CapacityExceededException;

    /**
     * Closes this memory manager and tells to release any used resources.
     */
    void close();

    /**
     * @return the default size (in bytes) of {@link VirtualMemorySegment}s that can be acquired via
     * {@link #requestDefaultMemory()}
     */
    int getDefaultSegmentSize();

    /**
     * @return the main memory capacity (in bytes) assigned to this {@link MemoryManager}
     */
    long getCapacity();

    /**
     * @return the main memory capacity (in bytes) used by this {@link MemoryManager}
     */
    long getUsedCapacity();

    /**
     * Change the capacity of managed main memory.
     *
     * @param capacity the new capacity in bytes
     * @throws CapacityExceededException if the capacity is lowered and the current state of managed memory does not
     *                                   fit the new capacity
     */
    void resize(long capacity) throws CapacityExceededException;
}

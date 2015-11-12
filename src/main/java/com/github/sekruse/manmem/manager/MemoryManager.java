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
}

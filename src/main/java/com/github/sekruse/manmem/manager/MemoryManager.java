package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.memory.Memory;

/**
 * Interace to a memory manager.
 */
public interface MemoryManager {

    /**
     * Request the default share of memory served by this memory manager.
     *
     * @return a {@link Memory} representing the requested memory
     * @throws CapacityExceededException if the memory manager cannot serve the request due to missing capacities
     */
    Memory requestDefaultMemory() throws CapacityExceededException;

}

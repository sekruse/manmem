package com.github.sekruse.manmem.manager.capabilities;

import com.github.sekruse.manmem.memory.MainMemorySegment;

/**
 * This capability allows the owner of a corresponding reference to return memory to a
 * {@link com.github.sekruse.manmem.manager.MemoryManager}.
 */
public interface ReturnMemoryCapability {

    /**
     * Return a piece of memory to the manager. This ends the lifecycle of the memory object.
     *
     * @param mainMemorySegment the memory to be returned
     */
    void returnMemory(MainMemorySegment mainMemorySegment);

}

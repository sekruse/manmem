package com.github.sekruse.manmem.memory;

import com.github.sekruse.manmem.manager.capabilities.ReturnMemoryCapability;

/**
 * This class represents a piece of memory to clients. The physical location of the memory (RAM, disk) is not
 * determined.
 */
public class Memory {

    /**
     * The main memory segment that backs this memory. This segment may be {@code null} due to memory preemption.
     */
    private MainMemorySegment mainMemorySegment;

    /**
     * The capability to return this memory when it is no longer needed.
     */
    private final ReturnMemoryCapability returnMemoryCapability;

    public Memory(ReturnMemoryCapability returnMemoryCapability) {
        this.returnMemoryCapability = returnMemoryCapability;
    }

    /**
     * Release this memory so that it can be dropped or recycled. Calling this function declares that this memory
     * will no longer be used.
     */
    public void release() {
        this.returnMemoryCapability.returnMemory(this.mainMemorySegment);
    }

    /**
     * Sets up the {@link MainMemorySegment} that backs this memory
     *
     * @param mainMemorySegment the backing {@link MainMemorySegment}
     */
    public void setMainMemorySegment(MainMemorySegment mainMemorySegment) {
        if (this.mainMemorySegment != null) {
            throw new IllegalStateException("Cannot set a new segment for the memory. It already has a segment.");
        }

        this.mainMemorySegment = mainMemorySegment;
    }
}

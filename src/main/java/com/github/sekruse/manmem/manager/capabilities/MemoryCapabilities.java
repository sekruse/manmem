package com.github.sekruse.manmem.manager.capabilities;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.memory.DiskMemorySegment;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import com.github.sekruse.manmem.memory.Memory;

/**
 * This class encapsulates capabilities that are needed for {@link com.github.sekruse.manmem.memory.Memory} to operate.
 */
public interface MemoryCapabilities {

    /**
     * Loads the {@link com.github.sekruse.manmem.memory.MainMemorySegment} for {@link Memory} that has been spilled.
     *
     * @param memory the {@link Memory} that has been spilled
     * @throws CapacityExceededException if no {@link com.github.sekruse.manmem.memory.MainMemorySegment} could be
     *                                   obtained
     */
    void load(Memory memory) throws CapacityExceededException;

    /**
     * Return a piece of memory to the manager. This ends the lifecycle of the memory object.
     *
     * @param mainMemorySegment the main memory to be returned or {@code null}
     * @param diskMemorySegment the disk memory to be returned or {@code null}
     */
    void returnMemory(MainMemorySegment mainMemorySegment, DiskMemorySegment diskMemorySegment);

    /**
     * Puts a {@link MainMemorySegment} into a queue that corresponds to the {@link MainMemorySegment#getState()}.
     *
     * @param mainMemorySegment the {@link MainMemorySegment} to be enqueued
     */
    void enqueue(MainMemorySegment mainMemorySegment);

}
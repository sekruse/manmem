package com.github.sekruse.manmem.manager.capabilities;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.memory.DiskMemorySegment;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;

/**
 * This class encapsulates capabilities that are needed for {@link VirtualMemorySegment} to operate.
 */
public interface MemoryCapabilities {

    /**
     * Loads the {@link com.github.sekruse.manmem.memory.MainMemorySegment} for {@link VirtualMemorySegment} that has been spilled.
     *
     * @param virtualMemorySegment the {@link VirtualMemorySegment} that has been spilled
     * @throws CapacityExceededException if no {@link com.github.sekruse.manmem.memory.MainMemorySegment} could be
     *                                   obtained
     */
    void load(VirtualMemorySegment virtualMemorySegment) throws CapacityExceededException;

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

    /**
     * Writes the {@link MainMemorySegment} of the given {@link VirtualMemorySegment}. Assumes that the
     * {@link VirtualMemorySegment#getMainMemorySegmentLock()} is held.
     *
     * @param virtualMemorySegment the {@link VirtualMemorySegment} to back
     */
    void back(VirtualMemorySegment virtualMemorySegment);
}

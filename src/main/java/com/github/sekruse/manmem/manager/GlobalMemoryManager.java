package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.manager.capabilities.ReturnMemoryCapability;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import com.github.sekruse.manmem.memory.Memory;
import com.github.sekruse.manmem.util.QueueableQueue;

/**
 * A global memory manager is a first class memory manager, i.e., it does not depend on other memory managers.
 */
public class GlobalMemoryManager implements MemoryManager {

    /**
     * The capacity of this memory manager in bytes. Note that this pertains only to the payload data that can be
     * assigned to clients. The memory manager will also require some main memory for its management data structures.
     */
    private long capacity;

    /**
     * The used memory for created buffers in bytes. Must not exceed {@link #capacity}.
     */
    private long capacityUsage = 0L;

    /**
     * The capacity of a default piece of memory.
     */
    private final int defaultMemorySize;

    /**
     * A queue of {@link MainMemorySegment}s that could be spilled to disk.
     */
    private final QueueableQueue<MainMemorySegment> spillQueue = new QueueableQueue<>();

    /**
     * This capability allows to return memory.
     */
    private final ReturnMemoryCapability returnMemoryCapability = (mainMemorySegment -> {
        if (mainMemorySegment == null) {
            throw new IllegalArgumentException();
        }

        this.capacityUsage = mainMemorySegment.capacity();
    });

    /**
     * Creates a new memory manager.
     *
     * @param capacity          the amount of memory that the new memory manager can issue
     * @param defaultMemorySize the size of default memory segments
     */
    public GlobalMemoryManager(long capacity, int defaultMemorySize) {
        this.capacity = capacity;
        this.defaultMemorySize = defaultMemorySize;
    }


    @Override
    public Memory requestDefaultMemory() throws CapacityExceededException {
        MainMemorySegment mainMemorySegment = obtainDefaultMainMemorySegment();
        Memory memory = new Memory(this.returnMemoryCapability);
        mainMemorySegment.assignTo(memory);
        return memory;
    }

    /**
     * This method tries to obtain a {@link MainMemorySegment} anyhow without violating the {@link #capacity}.
     *
     * @return the obtained {@link MainMemorySegment}
     */
    private MainMemorySegment obtainDefaultMainMemorySegment() throws CapacityExceededException {
        // 1. look for a free segment
        // TODO

        // 2. if possible, create a new segment
        if (this.capacityUsage + this.defaultMemorySize <= this.capacity) {
            return createNewDefaultMainMemorySegment();
        }

        // 3. try to steal a backed memory segment
        // TODO

        // 4. try to back a memory segment, then steal it
        // TODO

        throw new CapacityExceededException("Could not obtain the requested memory segment.");
    }

    /**
     * Creates a new default {@link MainMemorySegment}.
     *
     * @return the created instance
     */
    private MainMemorySegment createNewDefaultMainMemorySegment() {
        if (this.capacityUsage + this.defaultMemorySize > this.capacity) {
            throw new CapacityExceededException("Capacity does not allow to create a new memory segment.");
        }

        MainMemorySegment mainMemorySegment = new MainMemorySegment(this.defaultMemorySize);
        this.capacityUsage += this.defaultMemorySize;
        return mainMemorySegment;
    }
}

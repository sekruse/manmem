package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.io.DiskOperator;
import com.github.sekruse.manmem.manager.capabilities.MemoryCapabilities;
import com.github.sekruse.manmem.memory.DiskMemorySegment;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;
import com.github.sekruse.manmem.memory.SegmentState;
import com.github.sekruse.manmem.util.QueueableQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * A global memory manager is a first class memory manager, i.e., it does not depend on other memory managers.
 */
public class GlobalMemoryManager implements MemoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalMemoryManager.class);

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
     * A queue of {@link MainMemorySegment}s that are backed.
     */
    private final QueueableQueue<MainMemorySegment> backedQueue = new QueueableQueue<>();

    /**
     * A queue of {@link MainMemorySegment}s that are free and can be used.
     */
    private final QueueableQueue<MainMemorySegment> freeQueue = new QueueableQueue<>();

    /**
     * A {@link DiskOperator} for using disk memory.
     */
    private final DiskOperator diskOperator;

    /**
     * Capabilities that are granted to managed {@link VirtualMemorySegment} objects.
     */
    private final MemoryCapabilities memoryCapabilities = new MemoryCapabilities() {
        @Override
        public void load(VirtualMemorySegment virtualMemorySegment) throws CapacityExceededException {
            // Do some sanity checks.
            if (virtualMemorySegment == null) {
                throw new IllegalStateException();
            }
            if (virtualMemorySegment.getMainMemorySegment() != null) {
                LOGGER.warn("Requested to load a main memory segment that is already there.");
                return;
            }
            final DiskMemorySegment diskMemorySegment = virtualMemorySegment.getDiskMemorySegment();
            if (diskMemorySegment == null) {
                throw new IllegalStateException();
            }

            // Obtain a free MainMemorySegment.
            final MainMemorySegment freeSegment = obtainFreeMainMemorySegment();
            if (freeSegment == null) {
                throw new CapacityExceededException("Could not obtain free segment to load spilled memory.");
            }

            // Load the DiskMemorySegment into the MainMemorySegment.
            final DiskOperator diskOperator = diskMemorySegment.getDiskOperator();
            try {
                diskOperator.load(diskMemorySegment, freeSegment);
            } catch (IOException e) {
                throw new ManagedMemoryException("Could not load a memory segment from disk.", e);
            }

            // Update the MainMemorySegment state and integrate it into the Memory.
            freeSegment.assignTo(virtualMemorySegment);
            freeSegment.setState(SegmentState.BACKED);
            enqueue(freeSegment);
        }

        @Override
        public void returnMemory(MainMemorySegment mainMemorySegment, DiskMemorySegment diskMemorySegment) {
            if (mainMemorySegment != null) {
                mainMemorySegment.reset();
                enqueue(mainMemorySegment);
            }

            if (diskMemorySegment != null) {
                diskMemorySegment.free();
            }
        }

        @Override
        public void enqueue(MainMemorySegment mainMemorySegment) {
            // Make sure that the segment is not in a queue anymore.
            mainMemorySegment.unlink();

            // Determine an appropriate queue.
            switch (mainMemorySegment.getState()) {
                case FREE:
                    GlobalMemoryManager.this.freeQueue.add(mainMemorySegment);
                    break;
                case BACKED:
                    GlobalMemoryManager.this.backedQueue.add(mainMemorySegment);
                    break;
                case DIRTY:
                    GlobalMemoryManager.this.spillQueue.add(mainMemorySegment);
                    break;
                default:
                    throw new IllegalStateException("Unknown/unhandled segment state: " + mainMemorySegment.getState());
            }
        }
    };

    /**
     * Creates a new memory manager.
     *
     * @param capacity          the amount of memory that the new memory manager can issue
     * @param defaultMemorySize the size of default memory segments
     * @throws IOException if the spill files cannot be created/accessed
     */
    public GlobalMemoryManager(long capacity, int defaultMemorySize) throws ManagedMemoryException {
        this(capacity, defaultMemorySize, null);
    }

    /**
     * Creates a new memory manager.
     *
     * @param capacity          the amount of memory that the new memory manager can issue
     * @param defaultMemorySize the size of default memory segments
     * @param spillDirectory    a directory for spilling memory segments or {@code null} for the temp folder
     * @throws IOException if the spill files cannot be created/accessed
     */
    public GlobalMemoryManager(long capacity, int defaultMemorySize, File spillDirectory) throws ManagedMemoryException {
        try {
            this.capacity = capacity;
            this.defaultMemorySize = defaultMemorySize;
            File spillFile = null;
            spillFile = spillDirectory == null ?
                    File.createTempFile("manmem", "segments") :
                    File.createTempFile("manmem", "segments", spillDirectory);
            this.diskOperator = new DiskOperator(spillFile, this.defaultMemorySize);
        } catch (IOException e) {
            throw new ManagedMemoryException("Could not create the memory manager.", e);
        }
    }


    @Override
    public VirtualMemorySegment requestDefaultMemory() throws CapacityExceededException {
        // Get a free memory segment.
        MainMemorySegment mainMemorySegment = obtainFreeMainMemorySegment();

        VirtualMemorySegment virtualMemorySegment = new VirtualMemorySegment(this.memoryCapabilities);
        mainMemorySegment.setState(SegmentState.DIRTY);
        this.memoryCapabilities.enqueue(mainMemorySegment);
        mainMemorySegment.assignTo(virtualMemorySegment);
        return virtualMemorySegment;
    }

    /**
     * This method tries to obtain a {@link MainMemorySegment} anyhow without violating the {@link #capacity}.
     *
     * @return the obtained free {@link MainMemorySegment}
     */
    private MainMemorySegment obtainFreeMainMemorySegment() throws CapacityExceededException {
        // 1. look for a free segment
        final MainMemorySegment recycledSegment = this.freeQueue.poll();
        if (recycledSegment != null) {
            return recycledSegment;
        }

        // 2. if possible, create a new segment
        if (this.capacityUsage + this.defaultMemorySize <= this.capacity) {
            return createNewDefaultMainMemorySegment();
        }

        // 3. try to steal a backed memory segment
        final MainMemorySegment backedMemorySegment = this.backedQueue.poll();
        if (backedMemorySegment != null) {
            if (backedMemorySegment.getOwner().yieldMainMemory() != backedMemorySegment) {
                throw new IllegalStateException("The segment/owner relationship seems to be broken.");
            }
            backedMemorySegment.reset();
            return backedMemorySegment;
        }

        // 4. try to back a memory segment, then steal it
        try {
            final MainMemorySegment stolenSegment = backAndStealDefaultMainMemorySegment();
            if (stolenSegment != null) {
                return stolenSegment;
            }
        } catch (IOException e) {
            throw new ManagedMemoryException(e);
        }

        throw new CapacityExceededException("Could not obtain the requested memory segment.");
    }

    /**
     * Try to spill a segment from the {@link #spillQueue} so that it can stolen. Then steal it directly.
     *
     * @return the stolen free {@link MainMemorySegment} or {@code null} if none could be stolen
     */
    private MainMemorySegment backAndStealDefaultMainMemorySegment() throws IOException {
        // Find a spillable main memory segment.
        final MainMemorySegment spillableSegment = spillQueue.poll();
        if (spillableSegment == null) {
            return null;
        }
        spillableSegment.shouldBeInState(SegmentState.DIRTY);

        // Revoke the memory from its owner.
        final VirtualMemorySegment owner = spillableSegment.getOwner();
        if (owner.yieldMainMemory() != spillableSegment) {
            throw new RuntimeException("Relationship between main memory segment and owner seems to be broken.");
        }

        // Determine whether there already is a disk memory segment for this main memory segment.
        // Then spill the main memory segment.
        DiskMemorySegment diskMemorySegment = owner.getDiskMemorySegment();
        if (diskMemorySegment != null) {
            this.diskOperator.write(spillableSegment, diskMemorySegment);
        } else {
            diskMemorySegment = this.diskOperator.write(spillableSegment);
            owner.setDiskMemorySegment(diskMemorySegment);
        }

        // Reset and deliver the main memory segment.
        spillableSegment.reset();
        return spillableSegment;
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

    @Override
    public void close() {
        try {
            this.diskOperator.close();
        } catch (Exception e) {
            LOGGER.error("Could not close the disk operator.", e);
        }
    }
}

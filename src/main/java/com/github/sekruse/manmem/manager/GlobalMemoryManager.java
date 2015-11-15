package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.io.DiskOperator;
import com.github.sekruse.manmem.manager.capabilities.MemoryCapabilities;
import com.github.sekruse.manmem.memory.DiskMemorySegment;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import com.github.sekruse.manmem.memory.SegmentState;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;
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
            mainMemorySegment.shouldBeUnlinked();

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

        @Override
        public void back(VirtualMemorySegment virtualMemorySegment) {
            final MainMemorySegment mms = virtualMemorySegment.getMainMemorySegment();
            if (mms == null || mms.getState() != SegmentState.DIRTY) {
                return;
            }

            try {
                spill(mms);
            } catch (IOException e) {
                throw new ManagedMemoryException(e);
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

        // Wrap it and set it up.
        mainMemorySegment.setState(SegmentState.DIRTY);
        this.memoryCapabilities.enqueue(mainMemorySegment);
        VirtualMemorySegment virtualMemorySegment = new VirtualMemorySegment(this.memoryCapabilities);
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
        final MainMemorySegment recycledSegment = drawFreeSegment();
        if (recycledSegment != null) {
            return recycledSegment;
        }

        // 2. if possible, create a new segment
        final MainMemorySegment newMainMemorySegment = tryToCreateNewDefaultMainMemorySegment();
        if (newMainMemorySegment != null) {
            return newMainMemorySegment;
        }

        // 3. try to steal a backed memory segment
        final MainMemorySegment backedMemorySegment = drawBackedSegment();
        if (backedMemorySegment != null) {
            return backedMemorySegment;
        }

        // 4. try to back a memory segment, then steal it
        try {
            final MainMemorySegment stolenSegment = drawDirtySegment();
            if (stolenSegment != null) {
                return stolenSegment;
            }
        } catch (IOException e) {
            throw new ManagedMemoryException(e);
        }

        throw new CapacityExceededException("Could not obtain the requested memory segment.");
    }

    /**
     * Draw a {@link MainMemorySegment} from the {@link #freeQueue}.
     *
     * @return the drawn {@link MainMemorySegment} or {@code null} if none was available
     */
    private MainMemorySegment drawFreeSegment() {
        final MainMemorySegment freeSegment = this.freeQueue.poll();
        if (freeSegment != null) {
            freeSegment.shouldBeInState(SegmentState.FREE);
            if (freeSegment.getOwner() != null) {
                throw new IllegalStateException();
            }
        }
        return freeSegment;
    }

    /**
     * Draws a {@link MainMemorySegment} from the {@link #backedQueue} and resets it.
     *
     * @return the drawn {@link MainMemorySegment} or {@code null} if none was available
     */
    private MainMemorySegment drawBackedSegment() {
        final MainMemorySegment backedMemorySegment = this.backedQueue.poll(); // NB: Polling locks the owner.
        if (backedMemorySegment != null) {
            backedMemorySegment.shouldBeInState(SegmentState.BACKED);
            if (backedMemorySegment.getOwner().yieldMainMemory() != backedMemorySegment) {
                throw new IllegalStateException("The segment/owner relationship seems to be broken.");
            }
            backedMemorySegment.getOwner().getMainMemorySegmentLock().unlock();
            backedMemorySegment.reset();
        }
        return backedMemorySegment;
    }

    /**
     * Try to spill a segment from the {@link #spillQueue} so that it can be revoked. Then revoke it directly.
     *
     * @return the stolen free {@link MainMemorySegment} or {@code null} if none could be stolen
     */
    private MainMemorySegment drawDirtySegment() throws IOException {
        // Find a spillable main memory segment.
        final MainMemorySegment spillableSegment = spillQueue.poll(); // NB: Polling yields a lock on the owner.
        if (spillableSegment == null) {
            return null;
        }
        spillableSegment.shouldBeInState(SegmentState.DIRTY);
        spillAndRevoke(spillableSegment);

        // Reset and deliver the main memory segment.
        spillableSegment.reset();
        return spillableSegment;
    }

    /**
     * Spill a given {@link MainMemorySegment} to disk. This method assumes that the calling thread holds the
     * associated {@link VirtualMemorySegment#getMainMemorySegmentLock()} and releases it afterwards. Also, it
     * sets up the {@link VirtualMemorySegment#setDiskMemorySegment(DiskMemorySegment)} properly.
     *
     * @param spillableSegment the segment to spill
     * @throws IOException
     */
    private void spillAndRevoke(MainMemorySegment spillableSegment) throws IOException {
        spill(spillableSegment);
        VirtualMemorySegment owner = revoke(spillableSegment);
        owner.getMainMemorySegmentLock().unlock();
    }

    /**
     * Spill a given {@link MainMemorySegment} to disk. This method assumes that the calling thread holds the
     * associated {@link VirtualMemorySegment#getMainMemorySegmentLock()}. Also, it
     * sets up the {@link VirtualMemorySegment#setDiskMemorySegment(DiskMemorySegment)} properly.
     *
     * @param spillableSegment the segment to spill
     * @throws IOException
     */
    private void spill(MainMemorySegment spillableSegment) throws IOException {
        // Find the owner.
        final VirtualMemorySegment owner = spillableSegment.getOwner();
        if (owner == null) {
            throw new IllegalStateException();
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
        spillableSegment.setState(SegmentState.BACKED);
    }

    /**
     * Revokes a {@link MainMemorySegment} from its {@link VirtualMemorySegment}. This method assumes that the calling thread holds the
     * associated {@link VirtualMemorySegment#getMainMemorySegmentLock()}.
     *
     * @param mms the segment to spill
     * @throws IOException
     * @return the former {@link MainMemorySegment#getOwner()}
     */
    private VirtualMemorySegment revoke(MainMemorySegment mms) throws IOException {
        // Revoke the memory from its owner.
        final VirtualMemorySegment owner = mms.getOwner();
        if (owner.yieldMainMemory() != mms) {
            throw new RuntimeException("Relationship between main memory segment and owner seems to be broken.");
        }
        return owner;
    }

    /**
     * Creates a new default {@link MainMemorySegment}.
     *
     * @return the created instance or {@code null} if there are no remaining capacities
     */
    synchronized private MainMemorySegment tryToCreateNewDefaultMainMemorySegment() {
        if (this.capacityUsage + this.defaultMemorySize > this.capacity) {
            return null;
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

    @Override
    public int getDefaultSegmentSize() {
        return this.defaultMemorySize;
    }

    @Override
    public long getCapacity() {
        return this.capacity;
    }

    @Override
    public long getUsedCapacity() {
        return this.capacityUsage;
    }

    @Override
    synchronized public void resize(long newCapacity) throws CapacityExceededException {
        if (newCapacity < 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = newCapacity;

        // If we need to shrink the main memory usage, go to the free segments at first.
        while (this.capacityUsage > this.capacity) {
            // Try to get a backed segment.
            final MainMemorySegment freeSegment = drawFreeSegment();
            if (freeSegment == null) break;
            this.capacityUsage -= freeSegment.capacity();
        }

        // Next, go to the backed segments.
        while (this.capacityUsage > this.capacity) {
            // Try to get a backed segment.
            final MainMemorySegment backedSegment = drawBackedSegment();
            if (backedSegment == null) break;
            this.capacityUsage -= backedSegment.capacity();
        }

        // When the eviction of free and backed segments was not sufficient, spill dirty segments and steal them.
        while (this.capacityUsage > this.capacity) {
            // Try to get a backed segment.
            final MainMemorySegment dirtySegment;
            try {
                dirtySegment = drawDirtySegment();
            } catch (IOException e) {
                throw new ManagedMemoryException("Could not spill dirty segment when resizing the managed memory.", e);
            }
            if (dirtySegment == null) break;
            this.capacityUsage -= dirtySegment.capacity();
        }

        if (this.capacityUsage > this.capacity) {
            this.capacity = this.capacityUsage;
            throw new CapacityExceededException("Could not resize the capacity as requested.");
        }
    }

    @Override
    public String toString() {
        return String.format("GlobalMemoryManager[%d MB, %.1f%% used]",
                capacity >>> 20, 100d * capacityUsage / capacity);
    }
}

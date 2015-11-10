package com.github.sekruse.manmem.memory;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.capabilities.MemoryCapabilities;

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
     * The disk memory segment that backs this memory.
     */
    private DiskMemorySegment diskMemorySegment;

    /**
     * Some capabilities to operate with a {@link com.github.sekruse.manmem.manager.MemoryManager}.
     */
    private final MemoryCapabilities capabilities;

    /**
     * Creates a new instance.
     *
     * @param capabilities the capabilities needed to operate with a
     *                     {@link com.github.sekruse.manmem.manager.MemoryManager}.
     */
    public Memory(MemoryCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Release this memory so that it can be dropped or recycled. Calling this function declares that this memory
     * will no longer be used.
     */
    public void release() {
        this.capabilities.returnMemory(this.mainMemorySegment, this.diskMemorySegment);
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

    /**
     * Sets up the {@link DiskMemorySegment} that backs this memory.
     *
     * @param diskMemorySegment the backing {@link DiskMemorySegment}
     */
    public void setDiskMemorySegment(DiskMemorySegment diskMemorySegment) {
        this.diskMemorySegment = diskMemorySegment;
    }

    /**
     * @return the backing {@link MainMemorySegment}
     */
    public MainMemorySegment getMainMemorySegment() {
        return mainMemorySegment;
    }

    /**
     * @return the backing {@link DiskMemorySegment}
     */
    public DiskMemorySegment getDiskMemorySegment() {
        return diskMemorySegment;
    }

    /**
     * Yield the {@link MainMemorySegment}. The segment will be delivered in its current state.
     *
     * @return the yielded {@link MainMemorySegment}
     * @throws IllegalStateException if there is no {@link MainMemorySegment} to yield
     */
    public MainMemorySegment yieldMainMemory() {
        if (this.mainMemorySegment == null) {
            throw new IllegalStateException("Cannot yield main memory segment.");
        }

        final MainMemorySegment mainMemorySegment = this.mainMemorySegment;
        this.mainMemorySegment = null;

        return mainMemorySegment;
    }

    /**
     * Unlike {@link #getMainMemorySegment()}, this method will try to load the {@link MainMemorySegment} if it was
     * spilled. If it was loaded, it will also be available via {@link #getMainMemorySegment()} unless it is spilled
     * again.
     *
     * @return the {@link MainMemorySegment}
     * @throws CapacityExceededException if the {@link MainMemorySegment} could not be retrieved due to lack of
     *                                   capacities
     */
    public MainMemorySegment ensureMainMemorySegment() throws CapacityExceededException {
        if (this.mainMemorySegment != null) {
            return this.mainMemorySegment;
        }

        if (this.diskMemorySegment == null) {
            throw new IllegalStateException("Neither a main memory segment nor a disk segment given.");
        }

        this.capabilities.load(this);
        if (this.mainMemorySegment == null) {
            throw new IllegalStateException("Still no main memory segment present after loading.");
        }

        return this.mainMemorySegment;
    }

    /**
     * Enqueues the {@link #mainMemorySegment} of the object into a suitable queue in
     * {@link com.github.sekruse.manmem.manager.MemoryManager}.
     */
    public void enqueue() {
        if (this.mainMemorySegment == null) {
            throw new IllegalStateException("Cannot enqueue main memory segment, because there is none.");
        }
        this.capabilities.enqueue(this.mainMemorySegment);
    }

    /**
     * Grants {@link ReadAccess} to this memory.
     *
     * @return a {@link ReadAccess}, which must be closed after use!
     * @see {@link AutoCloseable#close()}
     */
    public ReadAccess getReadAccess() {
        // Load the MainMemorySegment if necessary.
        final MainMemorySegment mainMemorySegment = ensureMainMemorySegment();

        // Remove it from any queue so that it is not preempted.
        mainMemorySegment.unlink();

        // Wrap the memory segment in a read access.
        return new ReadAccess(this);
    }

    /**
     * Grants {@link WriteAccess} to this memory.
     *
     * @return a {@link WriteAccess}, which must be closed after use!
     * @see {@link AutoCloseable#close()}
     */
    public WriteAccess getWriteAccess() {
        // Load the MainMemorySegment if necessary.
        final MainMemorySegment mainMemorySegment = ensureMainMemorySegment();

        // Remove it from any queue so that it is not preempted.
        mainMemorySegment.unlink();

        // Wrap the memory segment in a read access.
        return new WriteAccess(this);
    }
}

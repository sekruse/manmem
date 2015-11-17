package com.github.sekruse.manmem.memory;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.capabilities.MemoryAccessException;
import com.github.sekruse.manmem.manager.capabilities.MemoryCapabilities;
import com.github.sekruse.manmem.util.QueueableQueue;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class represents a piece of memory to clients. The physical location of the memory (RAM, disk) is not
 * determined.
 */
public class VirtualMemorySegment {

    /**
     * The number maximum concurrent reads.
     * We do not actually need to restrict this number unless there are performance implications.
     *
     * @see #readSemaphore
     */
    private static final int MAX_CONCURRENT_READS = 1024;

    /**
     * {@link Semaphore} to regulate read and write accesses.
     */
    private Semaphore readSemaphore = new Semaphore(MAX_CONCURRENT_READS, false);

    /**
     * {@link java.util.concurrent.locks.Lock} to regulate write accesses.
     */
    private ReentrantLock writeLock = new ReentrantLock();

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
     * Use this {@link java.util.concurrent.locks.Lock} to deque the {@link #mainMemorySegment}.
     */
    private ReentrantLock mmsLock = new ReentrantLock();

    /**
     * Creates a new instance.
     *
     * @param capabilities the capabilities needed to operate with a
     *                     {@link com.github.sekruse.manmem.manager.MemoryManager}.
     */
    public VirtualMemorySegment(MemoryCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Release this memory so that it can be dropped or recycled. Calling this function declares that this memory
     * will no longer be used.
     *
     * @throws MemoryAccessException if there is still some {@link MemoryAccess} on this VirtualMemorySegment
     */
    public void release() throws MemoryAccessException {
        if (this.writeLock.tryLock()) {
            if (this.readSemaphore.tryAcquire(MAX_CONCURRENT_READS)) {
                dequeMainMemorySegment();
                this.capabilities.returnMemory(this.mainMemorySegment, this.diskMemorySegment);
                this.readSemaphore.release(MAX_CONCURRENT_READS);
            } else {
                this.writeLock.unlock();
                throw new MemoryAccessException("Segment is still being read-accessed.");
            }
        } else {
            throw new MemoryAccessException("Segment is being write-accessed.");
        }
    }

    /**
     * Sets up the {@link MainMemorySegment} that backs this memory
     *
     * @param mainMemorySegment the backing {@link MainMemorySegment}
     */
    public void setMainMemorySegment(MainMemorySegment mainMemorySegment) {
        if (mainMemorySegment != null && getMainMemorySegment() != null) {
            throw new IllegalStateException("Cannot set a new segment for the virtualMemorySegment. It already has a segment.");
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
        if (getMainMemorySegment() == null) {
            throw new IllegalStateException("Cannot yield main memory segment.");
        }

        final MainMemorySegment mainMemorySegment = getMainMemorySegment();
        setMainMemorySegment(null);

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
    private MainMemorySegment ensureMainMemorySegment() throws CapacityExceededException {
        // Make sure that
        dequeMainMemorySegment();

        if (getMainMemorySegment() != null) {
            return getMainMemorySegment();
        }

        if (getDiskMemorySegment() == null) {
            throw new IllegalStateException("Neither a main memory segment nor a disk segment given.");
        }

        this.capabilities.load(this);
        if (getMainMemorySegment() == null) {
            throw new IllegalStateException("Still no main memory segment present after loading.");
        }

        return getMainMemorySegment();
    }

    /**
     * Grants {@link ReadAccess} to this memory.
     *
     * @return a {@link ReadAccess}, which must be closed after use!
     * @see {@link AutoCloseable#close()}
     */
    public ReadAccess getReadAccess() {
        // Acquire a read semaphore when there is no pending or active write request.
        this.writeLock.lock();
        this.readSemaphore.acquireUninterruptibly();
        this.writeLock.unlock();

        // Load the MainMemorySegment if necessary.
        ensureMainMemorySegment();

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
        // Mark the pending write request.
        this.writeLock.lock();

        // Wait for all reads to finish.
        this.readSemaphore.acquireUninterruptibly(MAX_CONCURRENT_READS);

        // Load the MainMemorySegment if necessary.
        ensureMainMemorySegment();

        // Wrap the memory segment in a read access.
        return new WriteAccess(this);
    }

    /**
     * Use this {@link java.util.concurrent.locks.Lock} before modifying/accessing the {@link #mainMemorySegment}.
     *
     * @return the {@link java.util.concurrent.locks.Lock}
     */
    public ReentrantLock getMainMemorySegmentLock() {
        return mmsLock;
    }

    /**
     * Dequeue the {@link #mainMemorySegment} if any. This inhibits other accesses to it.
     *
     * @return whether the {@link MainMemorySegment} was enqueued
     */
    private boolean dequeMainMemorySegment() {
        final ReentrantLock mmsLock = getMainMemorySegmentLock();
        boolean wasSegmentLinked = false;
        while (true) {
            // Loop until either there is no MainMemorySegment or we can lock it in its queue.
            mmsLock.lock();
            final MainMemorySegment mms = getMainMemorySegment();
            if (mms != null && !mms.tryLockQueue()) {
                mmsLock.unlock();
                Thread.yield();
                continue;
            }

            // Remove the MainMemorySegment from its queue.
            if (mms != null && mms.getQueue() != null) {
                final QueueableQueue<MainMemorySegment> queue = mms.getQueue();
                if (queue != null) {
                    wasSegmentLinked = mms.dequeue();
                    queue.getLock().unlock();
                }
            }

            mmsLock.unlock();

            return wasSegmentLinked;
        }
    }

    /**
     * If a {@link ReadAccess} has finished, it has to release its access.
     */
    public void notifyReadAccessDone() {
        this.readSemaphore.release();
        enqueueIfNotAccessed();
    }

    /**
     * If a {@link WriteAccess} has finished, it has to release its access.
     */
    public void notifyWriteAccessDone() {
        this.readSemaphore.release(MAX_CONCURRENT_READS);
        this.writeLock.unlock();
        enqueueIfNotAccessed();
    }


    /**
     * Enqueues the {@link #mainMemorySegment} of the object into a suitable queue in
     * {@link com.github.sekruse.manmem.manager.MemoryManager} if there is no pending access request to it.
     */
    synchronized void enqueueIfNotAccessed() {
        // synchronized: if two threads are racing to enqueue this segment + segment gets polled -> problem
        if (getMainMemorySegment() == null || !getMainMemorySegment().isUnlinked()) {
            return;
        }

        if (this.writeLock.tryLock() && this.readSemaphore.tryAcquire(MAX_CONCURRENT_READS)) {
            getMainMemorySegmentLock().lock();
            this.capabilities.enqueue(getMainMemorySegment());
            getMainMemorySegmentLock().unlock();
            this.readSemaphore.release(MAX_CONCURRENT_READS);
        }
        if (this.writeLock.isHeldByCurrentThread()) this.writeLock.unlock();
    }


    /**
     * Backs the {@link MainMemorySegment} to disk.
     */
    public void back() {
        if (!this.writeLock.tryLock()) {
            throw new MemoryAccessException("Cannot back segment that is currently being written.");
        }
        dequeMainMemorySegment();
        if (getMainMemorySegment() != null) {
            this.capabilities.back(this);
        }
        this.writeLock.unlock();
        enqueueIfNotAccessed();
    }
}

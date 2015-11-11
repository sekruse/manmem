package com.github.sekruse.manmem.memory;

import com.github.sekruse.manmem.util.Queueable;
import com.github.sekruse.manmem.util.QueueableQueue;

import java.nio.ByteBuffer;

/**
 * This class encapsulates an actual amount of main memory. Notice the concurrent modification requirements imposed
 * by {@link Queueable}.
 * <p>NB: This class can be subject to two stakeholders, namely a {@link QueueableQueue} and a
 * {@link VirtualMemorySegment}. Therefore, when an object of this class is polled via {@link QueueableQueue#poll()},
 * it will acquires the related {@link VirtualMemorySegment#getMainMemorySegmentLock()} to avoid synchronization
 * conflicts. This lock has to be released manually afterwards.</p>
 */
public class MainMemorySegment implements Queueable<MainMemorySegment> {

    /**
     * If this segment is in use, it is attached to a {@link VirtualMemorySegment} object.
     */
    private VirtualMemorySegment owner;

    /**
     * {@link MainMemorySegment}s can be queued according to {@link Queueable}. These fields realize the link requirements
     * of that interface.
     */
    private Queueable<MainMemorySegment> previous, next;

    /**
     * The {@link QueueableQueue} that contains the segment or {@code null} if there is no such queue.
     */
    private QueueableQueue<MainMemorySegment> queue;

    /**
     * The current state of this segment.
     */
    private SegmentState state = SegmentState.FREE;

    /**
     * The payload of this main memory segment that stores the actual data.
     */
    private final byte[] payload;

    /**
     * The position of the next writable byte within {@link #payload}.
     */
    private int payloadLimit = 0;

    /**
     * Creates a new instance with a payload of the given size.
     *
     * @param payloadSize the size of the payload of this segment
     */
    public MainMemorySegment(int payloadSize) {
        this.payload = new byte[payloadSize];
    }

    @Override
    public void setPreviousElement(Queueable<MainMemorySegment> element) {
        this.previous = element;
    }

    @Override
    public void setNextElement(Queueable<MainMemorySegment> element) {
        this.next = element;
    }

    @Override
    public Queueable<MainMemorySegment> getNextElement() {
        return this.next;
    }

    @Override
    public Queueable<MainMemorySegment> getPreviousElement() {
        return this.previous;
    }

    @Override
    public QueueableQueue<MainMemorySegment> getQueue() {
        return this.queue;
    }

    @Override
    public void setQueue(QueueableQueue<MainMemorySegment> queue) {
        this.queue = queue;
    }

    @Override
    public void notifyBeingPolled() {
        // If there is an owner, we obtain a lock on it.
        if (this.owner != null) {
            this.owner.getMainMemorySegmentLock().lock();
        }
    }

    /**
     * @return the payload capacity of this main memory segment in bytes
     */
    public long capacity() {
        return this.payload.length;
    }

    /**
     * Assigns this segment to a new owner.
     *
     * @param owner the {@link VirtualMemorySegment} to assign this segment to
     */
    public void assignTo(VirtualMemorySegment owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        this.owner = owner;
        this.owner.setMainMemorySegment(this);
    }

    /**
     * Experimental API.
     *
     * @return the payload of this memory segment as {@link ByteBuffer}
     */
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(this.payload, 0, this.payloadLimit);
    }

    /**
     * Experimental API. Updates the payload size by the limit of this buffer.
     *
     * @param buf the buffer to update from; must be retrieved by {@link #asByteBuffer()}
     * @see ByteBuffer#limit()
     */
    public void update(ByteBuffer buf) {
        if (!buf.hasArray() || buf.array() != this.payload) {
            throw new IllegalArgumentException("Buffer does belong to this main memory segment.");
        }

        this.payloadLimit = buf.limit();
    }

    /**
     * Brings this segment into its original state after instantiation.
     */
    public void reset() {
        unlink();
        this.owner = null;
        this.payloadLimit = 0;
        this.state = SegmentState.FREE;
        // We do not eliminate the payload. In particular for efficiency.
    }

    public VirtualMemorySegment getOwner() {
        return owner;
    }

    /**
     * Sanity-check the {@link #state} of this segment.
     *
     * @param state the assumed {@link SegmentState} of this segment
     * @throws IllegalStateException if the actual and assumed state are distinct
     */
    public void shouldBeInState(SegmentState state) throws IllegalStateException {
        if (this.state != state) {
            throw new IllegalStateException(String.format("Segment should be %s but it is %s.", state, this.state));
        }
    }

    public void setState(SegmentState state) {
        this.state = state;
    }

    public SegmentState getState() {
        return state;
    }

    /**
     * Removes this element from its queue. Assumes that the queue lock is held.
     */
    public void dequeue() {
        unlink();
        setQueue(null);
    }
}

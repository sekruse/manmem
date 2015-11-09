package com.github.sekruse.manmem.memory;

import com.github.sekruse.manmem.util.Queueable;

/**
 * This class encapsulates an actual amount of main memory.
 */
public class MainMemorySegment implements Queueable<MainMemorySegment> {

    /**
     * If this segment is in use, it is attached to a {@link Memory} object.
     */
    private Memory owner;

    /**
     * {@link MainMemorySegment}s can be queued according to {@link Queueable}. These fields realize the link requirements
     * of that interface.
     */
    private MainMemorySegment previous, next;

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
    public void setPreviousElement(MainMemorySegment element) {
        this.previous = element;
    }

    @Override
    public void setNextElement(MainMemorySegment element) {
        this.next = element;
    }

    @Override
    public MainMemorySegment getNextElement() {
        return this.next;
    }

    @Override
    public MainMemorySegment getPreviousElement() {
        return this.previous;
    }

    /**
     * @return the payload capacity of this main memory segment in bytes
     */
    public long capacity() {
        return this.payload.length;
    }

    /**
     * Assigns this segment to a new owner.
     * @param owner the {@link Memory} to assign this segment to
     */
    public void assignTo(Memory owner) {
        // Sanity checks.
        if (this.state != SegmentState.FREE) {
            final String msg = String.format("Cannot assign segment to a new owner. Its state is %s.", this.state);
            throw new IllegalStateException(msg);
        }

        if (owner == null) {
            throw new NullPointerException();
        }

        this.owner = owner;
        this.state = SegmentState.DIRTY;
        this.owner.setMainMemorySegment(this);
    }
}

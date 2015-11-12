package com.github.sekruse.manmem.memory;

import java.nio.ByteBuffer;

/**
 * Common interface for any kind of access to {@link VirtualMemorySegment}. The access is valid until the {@link #close()} method
 * has been called. An open access might exclude other accesses.
 */
public abstract class MemoryAccess implements AutoCloseable {

    /**
     * Guardian to check, once the access has been closed.
     */
    private boolean isClosed = false;

    /**
     * The payload memory that is being accessed.
     */
    protected final ByteBuffer payload;

    /**
     * The {@link VirtualMemorySegment} that is accessed by this object.
     */
    protected final VirtualMemorySegment virtualMemorySegment;

    /**
     * Creates a new access object for some memory.
     * @param virtualMemorySegment the {@link VirtualMemorySegment} that should be accessed
     */
    public MemoryAccess(VirtualMemorySegment virtualMemorySegment) {
        this.virtualMemorySegment = virtualMemorySegment;
        this.payload = virtualMemorySegment.getMainMemorySegment().asByteBuffer();
    }

    /**
     * @return the payload of the memory as {@link ByteBuffer} that is initially in read state
     */
    public ByteBuffer getPayload() {
        ensureNotClosed();
        return this.payload;
    }

    protected void ensureNotClosed() {
        if (this.isClosed) {
            throw new IllegalStateException(String.format("%s is already closed.", getClass().getSimpleName()));
        }
    }

    @Override
    public void close() {
        ensureNotClosed();
        this.isClosed = true;
    }


    /**
     * @return whether this {@link MemoryAccess} permits write operations
     */
    public abstract boolean permitsWrite();

    /**
     * @return whether this {@link MemoryAccess} permits read operations
     */
    public boolean permitsRead() {
        // We assume this as the general case.
        return true;
    }

}

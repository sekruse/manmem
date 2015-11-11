package com.github.sekruse.manmem.memory;

import java.nio.ByteBuffer;

/**
 * Common interface for any kind of access to {@link VirtualMemorySegment}. The access is valid until the {@link #close()} method
 * has been called. An open access might exclude other accesses.
 */
public class MemoryAccess implements AutoCloseable {

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

        // Remove the MainMemorySegment from its queue.
        MainMemorySegment mainMemorySegment = this.virtualMemorySegment.ensureMainMemorySegment();
        mainMemorySegment.unlink();
        this.payload = mainMemorySegment.asByteBuffer();
    }

    /**
     * @return the payload of the memory as {@link ByteBuffer} that is initially in read state
     */
    public ByteBuffer getPayload() {
        return this.payload;
    }

    @Override
    public void close() {
        // Let the concrete access implementation do any closing work.
        doClose();

        // Give the Memory back into control.
        this.virtualMemorySegment.enqueue();
    }

    /**
     * This method is called from {@link #close()} and can be overwritten to perform closing tasks..
     */
    protected void doClose() {
    }
}

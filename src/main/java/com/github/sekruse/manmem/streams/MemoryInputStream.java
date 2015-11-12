package com.github.sekruse.manmem.streams;

import com.github.sekruse.manmem.memory.ReadAccess;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * This class allows to access an {@link Iterable} of {@link VirtualMemorySegment}s in
 * terms of an {@link java.io.InputStream}.
 */
public class MemoryInputStream extends InputStream {

    /**
     * Iterates over memory segments to be read by this input stream.
     */
    private final Iterator<VirtualMemorySegment> vmsIterator;

    /**
     * The {@link VirtualMemorySegment} that is currently being iterated.
     */
    private VirtualMemorySegment currentVms = null;

    /**
     * {@link ReadAccess} to {@link #currentVms}.
     */
    private ReadAccess readAccess;

    /**
     * {@link ByteBuffer} for {@link #readAccess}.
     */
    private ByteBuffer readBuffer;

    /**
     * Creates a new instance.
     *
     * @param vmsIterable the {@link VirtualMemorySegment}s that should be iterated
     */
    public MemoryInputStream(Iterable<VirtualMemorySegment> vmsIterable) {
        this(vmsIterable.iterator());
    }

    /**
     * Creates a new instance.
     *
     * @param vmsIterator contains the {@link VirtualMemorySegment}s that should be iterated
     */
    public MemoryInputStream(Iterator<VirtualMemorySegment> vmsIterator) {
        this.vmsIterator = vmsIterator;
    }

    /**
     * If there is no {@link VirtualMemorySegment} being iterated currently, try to get a new one.
     *
     * @return whether there is a {@link VirtualMemorySegment} available after the method execution
     */
    private boolean updateCurrentVirtualMemorySegment() {
        if (this.currentVms == null && this.vmsIterator.hasNext()) {
            this.currentVms = this.vmsIterator.next();
            if (this.currentVms != null) {
                this.readAccess = this.currentVms.getReadAccess();
                this.readBuffer = this.readAccess.getPayload();
            }
        }
        return this.currentVms != null;
    }

    @Override
    public int read() {
        if (!updateCurrentVirtualMemorySegment()) {
            return -1;
        }
        final byte b = this.readBuffer.get();
        closeFinishedSegment();
        return b & 0xFF; // avoid adding leading 1s when casting byte to int
    }

    @Override
    public void close() {
        if (this.currentVms != null) closeSegment();
        try {
            super.close();
        } catch (IOException e) {
            // This is not expected, as the superclass declares the IOException but does nothing at all.
            e.printStackTrace();
        }
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (!updateCurrentVirtualMemorySegment()) {
            return -1;
        }
        final int numBytesToCopy = Math.min(len, this.readBuffer.remaining());
        this.readBuffer.get(b, off, numBytesToCopy);
        closeFinishedSegment();
        return numBytesToCopy;
    }

    @Override
    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    @Override
    public long skip(long n) {
        if (!updateCurrentVirtualMemorySegment() || n < 0) {
            return 0;
        }
        final int numRemainingBytes = this.readBuffer.remaining();
        closeSegment();
        return numRemainingBytes;
    }

    /**
     * If the currently iterated {@link VirtualMemorySegment} has been completely read, close it.
     */
    private void closeFinishedSegment() {
        if (this.readBuffer != null && !this.readBuffer.hasRemaining()) {
            closeSegment();
        }
    }

    /**
     * Closes the currently iterated {@link VirtualMemorySegment}.
     */
    private void closeSegment() {
        this.readBuffer = null;
        this.readAccess.close();
        this.readAccess = null;
        this.currentVms = null;
    }
}

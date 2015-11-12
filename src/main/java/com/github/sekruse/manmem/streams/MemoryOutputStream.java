package com.github.sekruse.manmem.streams;

import com.github.sekruse.manmem.manager.MemoryManager;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;
import com.github.sekruse.manmem.memory.WriteAccess;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * todo
 */
public class MemoryOutputStream extends OutputStream {

    /**
     * Collects created {@link VirtualMemorySegment}s.
     */
    private final List<VirtualMemorySegment> collector;

    /**
     * Provides new {@link VirtualMemorySegment}s.
     */
    private final MemoryManager memoryManager;

    /**
     * The currently written {@link VirtualMemorySegment}.
     */
    private VirtualMemorySegment currentVms;

    /**
     * {@link WriteAccess} to {@link #currentVms}.
     */
    private WriteAccess writeAccess;

    /**
     * {@link ByteBuffer} for {@link #writeAccess}.
     */
    private ByteBuffer writeBuffer;

    /**
     * Creates a new instance.
     *
     * @param memoryManager the {@link MemoryManager} from that {@link VirtualMemorySegment}s can be drawn
     */
    public MemoryOutputStream(MemoryManager memoryManager) {
        this(memoryManager, new LinkedList<>());
    }

    /**
     * Creates a new instance.
     *
     * @param memoryManager the {@link MemoryManager} from that {@link VirtualMemorySegment}s can be drawn
     * @param collector     a {@link List} that will collect the created {@link VirtualMemorySegment}s
     */
    public MemoryOutputStream(MemoryManager memoryManager, List<VirtualMemorySegment> collector) {
        this.memoryManager = memoryManager;
        this.collector = collector;
    }

    @Override
    public void write(byte[] b, int offset, int len) {
        while (len > 0) {
            this.updateCurrentVirtualMemorySegment();
            int numBytesToPut = Math.min(len, this.writeBuffer.remaining());
            this.writeBuffer.put(b, offset, numBytesToPut);
            offset += numBytesToPut;
            len -= numBytesToPut;
            closeFinishedSegment();
        }
    }

    @Override
    public void write(byte[] b) {
        this.write(b, 0, b.length);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close()  {
        if (this.currentVms != null) closeSegment();
        try {
            super.close();
        } catch (IOException e) {
            // This is not expected, as the superclass declares the IOException but does nothing at all.
            e.printStackTrace();
        }
    }

    @Override
    public void write(int b) {
        this.updateCurrentVirtualMemorySegment();
        this.writeBuffer.put((byte) b);
        closeFinishedSegment();
    }

    /**
     * If there is no {@link VirtualMemorySegment} being iterated currently, request a new one.
     */
    private void updateCurrentVirtualMemorySegment() {
        if (this.currentVms == null) {
            this.currentVms = this.memoryManager.requestDefaultMemory();
            this.collector.add(this.currentVms);
            this.writeAccess = this.currentVms.getWriteAccess();
            this.writeBuffer = this.writeAccess.getPayload();
            this.writeBuffer.clear();
        }
    }

    /**
     * If the currently iterated {@link VirtualMemorySegment} has been completely read, close it.
     */
    private void closeFinishedSegment() {
        if (this.writeBuffer != null && !this.writeBuffer.hasRemaining()) {
            closeSegment();
        }
    }

    /**
     * Closes the currently iterated {@link VirtualMemorySegment}.
     */
    private void closeSegment() {
        this.writeBuffer.flip();
        this.writeBuffer = null;
        this.writeAccess.close();
        this.writeAccess = null;
        this.currentVms = null;
    }

    /**
     * @return the collected created {@link VirtualMemorySegment}s
     */
    public List<VirtualMemorySegment> getCollector() {
        return collector;
    }
}

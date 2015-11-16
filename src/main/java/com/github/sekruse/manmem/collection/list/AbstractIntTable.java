package com.github.sekruse.manmem.collection.list;

import com.github.sekruse.manmem.collection.Lockable;
import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.MemoryManager;
import com.github.sekruse.manmem.memory.MemoryAccess;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;

import java.nio.ByteBuffer;

/**
 * This class represents an array that is backed by {@link VirtualMemorySegment}s.
 */
public class AbstractIntTable implements Lockable {

    /**
     * The {@link MemoryManager} that provides memory.
     */
    protected final MemoryManager memoryManager;

    /**
     * An array of allocated {@link VirtualMemorySegment}s.
     */
    protected final VirtualMemorySegment[] virtualMemorySegments;

    /**
     * An array of currently held {@link MemoryAccess}es (or {@code null} if none is held).
     */
    protected final MemoryAccess[] memoryAccesses;

    /**
     * @see MemoryManager#getDefaultSegmentSize()
     */
    protected final int defaultSegmentSize;

    /**
     * The size of this array.
     */
    protected final long sizeInInts;

    /**
     * The amount of managed memory consumed by this instance.
     */
    protected final long usedCapacity;

    /**
     * Creates a new instance.
     *
     * @param size          number of {@code int}s in the array
     * @param memoryManager {@link MemoryManager} that manages the memory that back this array
     */
    public AbstractIntTable(long size, MemoryManager memoryManager) {
        // Do some sanity checks.
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        if (memoryManager == null) {
            throw new IllegalArgumentException();
        }
        this.memoryManager = memoryManager;
        this.defaultSegmentSize = this.memoryManager.getDefaultSegmentSize();
        if (this.defaultSegmentSize % Integer.BYTES != 0) {
            final String msg = String.format("To use %s, the default segment size must be divisible by %d.",
                    getClass().getSimpleName(), Integer.BYTES);
            throw new IllegalArgumentException(msg);
        }

        // Allocate the data structures to hold VirtualMemorySegments and MemoryAccesses.
        long sizeInBytes = size * Integer.BYTES;
        int numRequiredSegments = (int) ((sizeInBytes + this.defaultSegmentSize - 1) / this.defaultSegmentSize);
        this.virtualMemorySegments = new VirtualMemorySegment[numRequiredSegments];
        this.memoryAccesses = new MemoryAccess[numRequiredSegments];

        // Allocate the actual VirtualMemorySegments.
        for (int i = 0; i < this.virtualMemorySegments.length; i++) {
            this.virtualMemorySegments[i] = this.memoryManager.requestDefaultMemory();
        }
        this.sizeInInts = size;
        this.usedCapacity = this.virtualMemorySegments.length * memoryManager.getDefaultSegmentSize();
    }

    /**
     * Sets the value at a given position. Note that the value at unwritten positions is undefined.
     *
     * @param pos   the position
     * @param value the new value
     */
    protected void set(long pos, int value) {
        // Figure out where to write.
        int segmentIndex = getSegmentIndex(pos);
        int segmentOffset = getSegmentOffset(pos, segmentIndex);

        // Ensure write access to the segment.
        MemoryAccess access = this.memoryAccesses[segmentIndex];
        boolean isExistingWriteAccess = access != null;
        if (isExistingWriteAccess) {
            if (!access.permitsWrite()) {
                throw new IllegalStateException("Existing access does not permit writes.");
            }
        } else {
            access = this.virtualMemorySegments[segmentIndex].getWriteAccess();
        }

        // Perform the write.
        final ByteBuffer payload = access.getPayload();
        if (payload.limit() <= segmentOffset) {
            payload.limit(segmentOffset + Integer.BYTES);
        }
        payload.putInt(segmentOffset, value);

        // Close ad-hoc write access.
        if (!isExistingWriteAccess) {
            access.close();
        }
    }

    /**
     * Gets the value at a given position. Note that the value at unwritten positions is undefined.
     *
     * @param pos the position
     */
    protected int get(long pos) {
        // Figure out where to write.
        int segmentIndex = getSegmentIndex(pos);
        int segmentOffset = getSegmentOffset(pos, segmentIndex);

        // Ensure write access to the segment.
        MemoryAccess access = this.memoryAccesses[segmentIndex];
        final boolean isExistingAccess = access != null;
        if (isExistingAccess) {
            if (!access.permitsRead()) {
                throw new IllegalStateException("Existing access does not permit reads.");
            }
        } else {
            access = this.virtualMemorySegments[segmentIndex].getReadAccess();
        }

        // Perform the write.
        final ByteBuffer payload = access.getPayload();
        if (payload.limit() <= segmentOffset) {
            payload.limit(segmentOffset + Integer.BYTES);
        }
        final int returnValue = payload.getInt(segmentOffset);

        // Close ad-hoc write access.
        if (!isExistingAccess) {
            access.close();
        }

        return returnValue;
    }

    /**
     * Retrieves the offset (in bytes) of the given element (by {@code pos}) in its {@link VirtualMemorySegment}.
     *
     * @param pos          the position of the element
     * @param segmentIndex the index of the {@link VirtualMemorySegment} within that the element resides
     * @return the offset
     */
    protected int getSegmentOffset(long pos, int segmentIndex) {
        return (int) ((pos << 2) % this.defaultSegmentSize);
    }

    /**
     * Retrieves the index of the {@link VirtualMemorySegment} that hosts the element at the specified position,
     * thereby checking that the position is valid.
     *
     * @param pos the position of the element
     * @return the index of the element
     */
    protected int getSegmentIndex(long pos) {
        if (pos >= this.sizeInInts || pos < 0) {
            final String msg = String.format("Illegal index: %d (must be between 0 and %d).", pos, this.sizeInInts);
            throw new IndexOutOfBoundsException(msg);
        }
        return (int) (pos / (this.defaultSegmentSize >>> 2)); // log2(Integer.BYTES)
    }


    @Override
    public void lockForRead() throws CapacityExceededException {
        for (int i = 0; i < this.virtualMemorySegments.length; i++) {
            final MemoryAccess memoryAccess = this.memoryAccesses[i];
            if (memoryAccess == null) {
                this.memoryAccesses[i] = this.virtualMemorySegments[i].getReadAccess();
            } else {
                if (!memoryAccess.permitsRead()) {
                    throw new IllegalStateException(String.format("Segment %d is locked without reading access.", i));
                }
            }
        }
    }

    @Override
    public void lockForWrite() throws CapacityExceededException, IllegalStateException {
        for (int i = 0; i < this.virtualMemorySegments.length; i++) {
            final MemoryAccess memoryAccess = this.memoryAccesses[i];
            if (memoryAccess == null) {
                this.memoryAccesses[i] = this.virtualMemorySegments[i].getWriteAccess();
            } else {
                if (!memoryAccess.permitsWrite()) {
                    throw new IllegalStateException(String.format("Segment %d is locked without reading access.", i));
                }
            }
        }
    }

    @Override
    public void unlock() {
        for (int i = 0; i < this.virtualMemorySegments.length; i++) {
            final MemoryAccess memoryAccess = this.memoryAccesses[i];
            if (memoryAccess != null) {
                memoryAccess.close();
                this.memoryAccesses[i] = null;
            }
        }
    }

    /**
     * Sets all fields in this table to a default value.
     *
     * @param defaultValue the default value to set
     */
    protected void clear(int defaultValue) {
        long pos = 0L;

        // Initialize 4 KB of the default value.
        final byte[] clearMask = new byte[4 * 1024];
        if (defaultValue != 0) {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(clearMask);
            byteBuffer.clear();
            while (byteBuffer.hasRemaining()) {
                byteBuffer.putInt(defaultValue);
            }
            byteBuffer.flip();
        }

        // Iterate over the segments and clear them one by one using the clearMask.
        for (int segmentIndex = 0; segmentIndex < this.virtualMemorySegments.length; segmentIndex++) {
            // Get write access.
            MemoryAccess memoryAccess = this.memoryAccesses[segmentIndex];
            boolean isExistingAccess = memoryAccess != null;
            if (isExistingAccess) {
                if (!memoryAccess.permitsWrite()) {
                    final String msg = String.format("Segment %d is being accessed but not for writing.", segmentIndex);
                    throw new IllegalStateException(msg);
                }
            } else {
                memoryAccess = this.virtualMemorySegments[segmentIndex].getWriteAccess();
            }

            // Clean the segment.
            final ByteBuffer payload = memoryAccess.getPayload();
            payload.clear();
            payload.limit((int) Math.min(payload.capacity(), this.sizeInInts * Integer.BYTES - pos));
            int bytesToPut;
            while ((bytesToPut = Math.min(payload.remaining(), clearMask.length)) > 0) {
                payload.put(clearMask, 0, bytesToPut);
            }
            pos += payload.limit();

            // Close the write access.
            if (!isExistingAccess) {
                memoryAccess.close();
            }
        }
    }

    @Override
    public long getUsedCapacity() {
        return this.usedCapacity;
    }
}

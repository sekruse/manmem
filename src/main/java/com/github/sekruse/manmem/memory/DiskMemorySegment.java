package com.github.sekruse.manmem.memory;

import com.github.sekruse.manmem.io.DiskOperator;

/**
 * A disk memory segment is a pointer to memory that has been written to disk. It is associated with a
 * {@link com.github.sekruse.manmem.io.DiskOperator} that created this segment.
 */
public class DiskMemorySegment {

    /**
     * This {@link DiskOperator} that created this segment.
     */
    private final DiskOperator diskOperator;

    /**
     * This offset within the file that is managed by the {@link DiskOperator}.
     */
    private final long fileOffset;

    /**
     * The size of the segment in bytes.
     */
    private int size = 0;

    /**
     * Creates a new instance.
     *
     * @param diskOperator the operator that is managing this segment
     * @param fileOffset   the offset within the file where this segment should be written
     */
    public DiskMemorySegment(DiskOperator diskOperator, long fileOffset) {
        this.diskOperator = diskOperator;
        this.fileOffset = fileOffset;
    }

    /**
     * @return the {@link DiskOperator} that manages this disk segment
     */
    public DiskOperator getDiskOperator() {
        return this.diskOperator;
    }

    /**
     * @return the offset of this disk segment within its file
     */
    public long getFileOffset() {
        return this.fileOffset;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void free() {
        this.diskOperator.recycle(this);
    }

    public int getSize() {
        return size;
    }
}

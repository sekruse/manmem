package com.github.sekruse.manmem.memory;

/**
 * This class encapsulate write access to {@link Memory}.
 */
public class WriteAccess extends MemoryAccess {

    /**
     * Tells whether the write access has actually altered the data. By default {@code true}.
     */
    private boolean isMemoryChanged = true;

    @Override
    public void close() {
        super.close();
    }

    /**
     * Creates a new access object for some memory.
     *
     * @param memory the memory to be written
     */
    public WriteAccess(Memory memory) {
        super(memory);
    }

    /**
     * Specifies whether the write access has actually changed the data. By default, a change is assumed.
     *
     * @param isMemoryChanged whether the data has changed
     */
    public void setMemoryChanged(boolean isMemoryChanged) {
        this.isMemoryChanged = isMemoryChanged;
    }

    @Override
    protected void doClose() {
        super.doClose();

        if (this.isMemoryChanged) {
            final MainMemorySegment mainMemorySegment = this.memory.getMainMemorySegment();
            mainMemorySegment.update(this.payload);
            mainMemorySegment.setState(SegmentState.DIRTY);
        }
    }
}

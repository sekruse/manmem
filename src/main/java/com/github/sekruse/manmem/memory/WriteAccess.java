package com.github.sekruse.manmem.memory;

/**
 * This class encapsulate write access to {@link VirtualMemorySegment}.
 */
public class WriteAccess extends MemoryAccess {

    /**
     * Tells whether the write access has actually altered the data. By default {@code true}.
     */
    private boolean isMemoryChanged = true;

    /**
     * Creates a new access object for some memory.
     *
     * @param virtualMemorySegment the memory to be written
     */
    public WriteAccess(VirtualMemorySegment virtualMemorySegment) {
        super(virtualMemorySegment);
    }

    /**
     * Specifies whether the write access has actually changed the data. By default, a change is assumed.
     *
     * @param isMemoryChanged whether the data has changed
     */
    public void setMemoryChanged(boolean isMemoryChanged) {
        ensureNotClosed();
        this.isMemoryChanged = isMemoryChanged;
    }

    @Override
    public void close() {
        super.close();

        if (this.isMemoryChanged) {
            final MainMemorySegment mainMemorySegment = this.virtualMemorySegment.getMainMemorySegment();
            mainMemorySegment.update(this.payload);
            mainMemorySegment.setState(SegmentState.DIRTY); // We might have to lock here. However, there is no
            // concurrent action.
        }

        this.virtualMemorySegment.notifyWriteAccessDone();
    }

    @Override
    public boolean permitsWrite() {
        return true;
    }
}

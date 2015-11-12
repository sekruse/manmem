package com.github.sekruse.manmem.io;

import com.github.sekruse.manmem.memory.DiskMemorySegment;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is the interface to write and read memory segments from disk.
 */
public class DiskOperator implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiskOperator.class);


    /**
     * Info variable. Keeps track on how often {@link MainMemorySegment}s are loaded.
     */
    public static final AtomicLong SEGMENT_LOADS = new AtomicLong();

    /**
     * Info variable. Keeps track on how often {@link MainMemorySegment}s are written.
     */
    public static final AtomicLong SEGMENT_WRITES = new AtomicLong();


    /**
     * The file were this operator writes to and reads from.
     */
    private final File file;

    /**
     * A read/write channel to {@link #file}.
     */
    private final FileChannel fileChannel;

    /**
     * The (maximum) size of segments.
     */
    private final int segmentSize;

    /**
     * Tracks positions that can be written to in the file.
     */
    private final FreePositionTracker freePositionTracker = new FreePositionTracker();


    /**
     * Creates a new instance that operates on the given file.
     *
     * @param file        the file to write to and read from
     * @param segmentSize the (maximum) size of segments to be written
     * @throws IOException if the file could not be opened
     */
    public DiskOperator(File file, int segmentSize) throws IOException {
        this.file = file;
        this.fileChannel = openFileChannel(this.file);
        this.segmentSize = segmentSize;
    }

    /**
     * Opens a read/write channel to {@link #file}.
     *
     * @return the opened channel
     * @throws IOException if the file could not be opened
     */
    private static FileChannel openFileChannel(File file) throws IOException {
        return FileChannel.open(file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.DELETE_ON_CLOSE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

    }

    /**
     * Writes a given {@link MainMemorySegment} to disk.
     *
     * @param mainMemorySegment the {@link MainMemorySegment} to write
     * @return the {@link DiskMemorySegment} that has been written
     * @throws IOException if the writting fails
     */
    public DiskMemorySegment write(MainMemorySegment mainMemorySegment) throws IOException {
        DiskMemorySegment diskMemorySegment = obtainFreeSegment();
        write(mainMemorySegment, diskMemorySegment);
        return diskMemorySegment;
    }

    /**
     * Obtain a free {@link DiskMemorySegment} that is associated to this operator.
     *
     * @return the obtained {@link DiskMemorySegment}
     */
    private DiskMemorySegment obtainFreeSegment() {
        final int freePosition = this.freePositionTracker.retrieveFreePosition();
        long freeOffset = freePosition * (long) this.segmentSize;
        return new DiskMemorySegment(this, freeOffset);
    }

    /**
     * Writes a {@link MainMemorySegment} to a file.
     *
     * @param mainMemorySegment the {@link MainMemorySegment} to write
     * @param diskMemorySegment the {@link DiskMemorySegment} to write to; must be managed by this operator
     * @throws IOException if the writing fails
     */
    public void write(MainMemorySegment mainMemorySegment, DiskMemorySegment diskMemorySegment) throws IOException {
        // Check that this operator is responsible for the passed segment.
        ensureResponsibility(diskMemorySegment);

        // Copy the main memory segment to disk.
        this.fileChannel.position(diskMemorySegment.getFileOffset());
        final ByteBuffer payload = mainMemorySegment.asByteBuffer();
        int writtenBytes = 0;
        while (writtenBytes < payload.limit()) {
            writtenBytes += this.fileChannel.write(payload);
        }

        // Update the metadata of the disk segment.
        diskMemorySegment.setSize(payload.limit());

        // Update the counter.
        SEGMENT_WRITES.incrementAndGet();
    }

    /**
     * Loads a {@link DiskMemorySegment} into a {@link MainMemorySegment}.
     *
     * @param diskMemorySegment the {@link DiskMemorySegment} to read
     * @param mainMemorySegment the {@link MainMemorySegment} to write to
     * @throws IOException if the loading from file fails
     */
    public void load(DiskMemorySegment diskMemorySegment, MainMemorySegment mainMemorySegment) throws IOException {
        ensureResponsibility(diskMemorySegment);

        // Copy the main memory segment to disk.
        this.fileChannel.position(diskMemorySegment.getFileOffset());
        final ByteBuffer payload = mainMemorySegment.asByteBuffer();
        payload.clear();
        payload.limit(diskMemorySegment.getSize());
        int bytesToRead = diskMemorySegment.getSize();
        while (bytesToRead > 0) {
            final int readBytes = this.fileChannel.read(payload);
            if (readBytes == -1) {
                throw new EOFException(String.format("EOF after %d bytes, expected %d bytes, though.",
                        readBytes, diskMemorySegment.getSize()));
            }
            bytesToRead -= readBytes;

        }
        payload.flip();
        if (payload.limit() != diskMemorySegment.getSize()) {
            LOGGER.warn("Need to reset the buffer limit from {} to {}.", payload.limit(), diskMemorySegment.getSize());
            payload.limit(diskMemorySegment.getSize());
        }

        // Update the main memory segment.
        mainMemorySegment.update(payload);

        // Update the counter.
        SEGMENT_LOADS.incrementAndGet();
    }

    /**
     * Make sure that this operator is responsible for the given {@link DiskMemorySegment}
     *
     * @param diskMemorySegment the {@link DiskMemorySegment} to check the responsiblity
     */
    private void ensureResponsibility(DiskMemorySegment diskMemorySegment) {
        if (diskMemorySegment.getDiskOperator() != this) {
            throw new IllegalArgumentException("Passed disk memory segment to wrong disk operator.");
        }
    }


    @Override
    public void close() throws Exception {
        try {
            this.fileChannel.close();
        } catch (IOException e) {
            LOGGER.error("Could not close file channel.", e);
        }
    }

    /**
     * Releases the given {@link DiskMemorySegment}, i.e., it is marked as free.
     *
     * @param diskMemorySegment the {@link DiskMemorySegment} to recycle
     */
    public void recycle(DiskMemorySegment diskMemorySegment) {
        ensureResponsibility(diskMemorySegment);
        long remainder = diskMemorySegment.getFileOffset() % this.segmentSize;
        if (remainder != 0L) {
            final String msg = String.format("The offset %d is not a multiple of the segment size %d.",
                    diskMemorySegment.getFileOffset(), this.segmentSize);
            throw new IllegalStateException(msg);
        }
        int position = (int) diskMemorySegment.getFileOffset() / this.segmentSize;
        this.freePositionTracker.addFreePosition(position);
    }
}

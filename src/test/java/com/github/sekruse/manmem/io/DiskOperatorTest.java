package com.github.sekruse.manmem.io;

import com.github.sekruse.manmem.memory.DiskMemorySegment;
import com.github.sekruse.manmem.memory.MainMemorySegment;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Tests for the {@link DiskOperator} class.
 */
public class DiskOperatorTest {

    @Test
    public void testSingleWritingAndReading() throws Exception {
        final int segmentSize = 8;
        final File diskOperatorFile = File.createTempFile("manmem", "segments");
        diskOperatorFile.deleteOnExit();

        // Create a disk operator.
        DiskOperator diskOperator = new DiskOperator(diskOperatorFile, segmentSize);

        final byte[] testData = {4, 3, 2, 1, 0, -1, -2};
        MainMemorySegment mainMemorySegment = createMainMemorySegment(segmentSize, testData);

        // Write the main memory segment to disk.
        final DiskMemorySegment diskMemorySegment = diskOperator.write(mainMemorySegment);
        loadAndCompareMainMemorySegment(diskMemorySegment, diskOperator, testData, segmentSize);


        diskOperator.close();
    }

    private void loadAndCompareMainMemorySegment(DiskMemorySegment diskMemorySegment, DiskOperator diskOperator, byte[] testData, int segmentSize) throws IOException {
        // Load the disk memory segment into a main memory segment.
        MainMemorySegment loadedMainMemorySegment = new MainMemorySegment(segmentSize);
        diskOperator.load(diskMemorySegment, loadedMainMemorySegment);

        // Compare the loaded main memory segment with the original data.
        final ByteBuffer loadedBuffer = loadedMainMemorySegment.asByteBuffer();
        Assert.assertEquals(testData.length, loadedBuffer.limit());
        final byte[] loadedArray = loadedBuffer.array();
        Assert.assertArrayEquals(testData, Arrays.copyOf(loadedArray, testData.length));
    }

    private MainMemorySegment createMainMemorySegment(int segmentSize, byte[] testData) {
        // Create a main memory segment with data.
        MainMemorySegment mainMemorySegment = new MainMemorySegment(segmentSize);
        final ByteBuffer byteBuffer = mainMemorySegment.asByteBuffer();
        byteBuffer.clear();
        byteBuffer.put(testData);
        byteBuffer.flip();
        mainMemorySegment.update(byteBuffer);
        return mainMemorySegment;
    }


    @Test
    public void testMultipleWritingsAndReadings() throws Exception {
        final int segmentSize = 8;
        final File diskOperatorFile = File.createTempFile("manmem", "segments");
        diskOperatorFile.deleteOnExit();

        // Create a disk operator.
        DiskOperator diskOperator = new DiskOperator(diskOperatorFile, segmentSize);

        // Create a main memory segment with data.
        byte[] testData1 = new byte[]{0, 1, 2, 3};
        MainMemorySegment mainMemorySegment1 = createMainMemorySegment(8, testData1);
        final DiskMemorySegment diskMemorySegment1 = diskOperator.write(mainMemorySegment1);

        byte[] testData2 = new byte[]{10, 10, 10, 10, -10, -10, -10, -10};
        MainMemorySegment mainMemorySegment2 = createMainMemorySegment(8, testData2);
        final DiskMemorySegment diskMemorySegment2 = diskOperator.write(mainMemorySegment2);

        byte[] testData3 = new byte[]{1, 2, 3, 4};
        MainMemorySegment mainMemorySegment3 = createMainMemorySegment(8, testData3);
        final DiskMemorySegment diskMemorySegment3 = diskOperator.write(mainMemorySegment3);

        byte[] testData4 = new byte[]{55, 66, 123, -23, -42};
        MainMemorySegment mainMemorySegment4 = createMainMemorySegment(8, testData4);
        final DiskMemorySegment diskMemorySegment4 = diskOperator.write(mainMemorySegment4);

        // Load the disk memory segment into a main memory segment.
        loadAndCompareMainMemorySegment(diskMemorySegment1, diskOperator, testData1, segmentSize);
        loadAndCompareMainMemorySegment(diskMemorySegment3, diskOperator, testData3, segmentSize);
        loadAndCompareMainMemorySegment(diskMemorySegment2, diskOperator, testData2, segmentSize);
        loadAndCompareMainMemorySegment(diskMemorySegment4, diskOperator, testData4, segmentSize);

        diskOperator.close();
    }

    @Test
    public void testRecycleDiskMemorySegment() throws Exception {
        final int segmentSize = 8;
        final File diskOperatorFile = File.createTempFile("manmem", "segments");
        diskOperatorFile.deleteOnExit();

        // Create a disk operator.
        DiskOperator diskOperator = new DiskOperator(diskOperatorFile, segmentSize);

        // Create a main memory segment with data.
        byte[] testData1 = new byte[]{0, 1, 2, 3};
        MainMemorySegment mainMemorySegment1 = createMainMemorySegment(8, testData1);
        final DiskMemorySegment diskMemorySegment1 = diskOperator.write(mainMemorySegment1);
        loadAndCompareMainMemorySegment(diskMemorySegment1, diskOperator, testData1, segmentSize);

        byte[] testData2 = new byte[]{10, 10, 10, 10, -10, -10, -10, -10};
        MainMemorySegment mainMemorySegment2 = createMainMemorySegment(8, testData2);
        final DiskMemorySegment diskMemorySegment2 = diskOperator.write(mainMemorySegment2);
        loadAndCompareMainMemorySegment(diskMemorySegment2, diskOperator, testData2, segmentSize);

        // Give up the first disk memory segment.
        diskMemorySegment1.free();

        byte[] testData3 = new byte[]{1, 2, 3, 4};
        MainMemorySegment mainMemorySegment3 = createMainMemorySegment(8, testData3);
        final DiskMemorySegment diskMemorySegment3 = diskOperator.write(mainMemorySegment3);
        loadAndCompareMainMemorySegment(diskMemorySegment3, diskOperator, testData3, segmentSize);

        byte[] testData4 = new byte[]{55, 66, 123, -23, -42};
        MainMemorySegment mainMemorySegment4 = createMainMemorySegment(8, testData4);
        final DiskMemorySegment diskMemorySegment4 = diskOperator.write(mainMemorySegment4);
        loadAndCompareMainMemorySegment(diskMemorySegment4, diskOperator, testData4, segmentSize);

        diskOperator.close();

        // The third segment should have reused the spot of the second segment.
        Assert.assertEquals(diskMemorySegment1.getFileOffset(), diskMemorySegment3.getFileOffset());
    }

}

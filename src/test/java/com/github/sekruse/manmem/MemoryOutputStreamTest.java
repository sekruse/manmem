package com.github.sekruse.manmem;

import com.github.sekruse.manmem.manager.GlobalMemoryManager;
import com.github.sekruse.manmem.manager.MemoryManager;
import com.github.sekruse.manmem.streams.MemoryInputStream;
import com.github.sekruse.manmem.streams.MemoryOutputStream;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test suite for {@link MemoryOutputStream} (and {@link MemoryInputStream}.
 */
public class MemoryOutputStreamTest {

    @Test
    public void testBulkWritingAndReading() {
        MemoryManager memoryManager = new GlobalMemoryManager(10, 2);
        final MemoryOutputStream memoryOutputStream = new MemoryOutputStream(memoryManager);
        byte[] testData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2, -3, -4, -5, -6, -7, -8, -9};
        memoryOutputStream.write(testData);
        memoryOutputStream.close();

        MemoryInputStream memoryInputStream = new MemoryInputStream(memoryOutputStream.getCollector());
        byte[] readData = new byte[testData.length];
        int overall = 0;
        while (overall < testData.length) {
            int bytes = memoryInputStream.read(readData, overall, testData.length - overall);
            if (bytes == -1) Assert.fail();
            overall += bytes;
        }
        Assert.assertEquals(-1, memoryInputStream.read(new byte[1]));

        Assert.assertArrayEquals(testData, readData);
    }

    @Test
    public void testSingleWritingAndReading() {
        MemoryManager memoryManager = new GlobalMemoryManager(10, 2);
        final MemoryOutputStream memoryOutputStream = new MemoryOutputStream(memoryManager);
        byte[] testData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2, -3, -4, -5, -6, -7, -8, -9};
        for (byte b : testData) {
            memoryOutputStream.write(b);
        }
        memoryOutputStream.close();

        MemoryInputStream memoryInputStream = new MemoryInputStream(memoryOutputStream.getCollector());
        int overall = 0;
        while (overall < testData.length) {
            int b = memoryInputStream.read();
            if (b == -1) Assert.fail();
            Assert.assertEquals(0xFF & testData[overall], b);
            overall++;
        }
        Assert.assertEquals(-1, memoryInputStream.read());

    }
    @Test
    public void testSkipping() {
        MemoryManager memoryManager = new GlobalMemoryManager(10, 2);
        final MemoryOutputStream memoryOutputStream = new MemoryOutputStream(memoryManager);
        byte[] testData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2, -3, -4, -5, -6, -7, -8, -9};
        for (byte b : testData) {
            memoryOutputStream.write(b);
        }
        memoryOutputStream.close();

        MemoryInputStream memoryInputStream = new MemoryInputStream(memoryOutputStream.getCollector());
        Assert.assertEquals(0xFF & testData[0], memoryInputStream.read());
        int toSkip = testData.length - 2;
        while (toSkip > 0) {
            toSkip -= memoryInputStream.skip(toSkip);
        }
        Assert.assertEquals(0xFF & testData[testData.length - 1], memoryInputStream.read());
        Assert.assertEquals(-1, memoryInputStream.read());
        memoryInputStream.close();
    }

    @Test
    public void testWritingAllocatesCorrectly() {
        MemoryManager memoryManager = new GlobalMemoryManager(10, 2);
        final MemoryOutputStream memoryOutputStream = new MemoryOutputStream(memoryManager);
        byte[] testData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        memoryOutputStream.write(testData);
        memoryOutputStream.write(new byte[0]);
        memoryOutputStream.close();

        // Each VMS can take 2 elements. So, we have certain expections on how many VMS are needed to fit the test data.
        Assert.assertEquals((testData.length + 2 - 1) / 2, memoryOutputStream.getCollector().size());
    }

}

package com.github.sekruse.manmem.streams;

import com.github.sekruse.manmem.manager.GlobalMemoryManager;
import com.github.sekruse.manmem.manager.MemoryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

/**
 * Test suite for {@link MemoryInputStream} and {@link MemoryOutputStream}.
 */
public class MemoryStreamTest {

    @Test
    public void testSkipping() {
        Random random = new Random(42);
        byte[] testData = new byte[1024];
        random.nextBytes(testData);
        MemoryManager memoryManager = new GlobalMemoryManager(2048, 128);

        MemoryOutputStream memoryOutputStream = new MemoryOutputStream(memoryManager);
        memoryOutputStream.write(testData);
        memoryOutputStream.close();

        MemoryInputStream memoryInputStream = new MemoryInputStream(memoryOutputStream.getCollector());
        Assert.assertEquals(0xFF & testData[0], memoryInputStream.read());
        long skip = 1022;
        while (skip > 0) {
            long skipped = memoryInputStream.skip(skip);
            if (skipped == 0) Assert.fail();
            skip -= skipped;
        }
        Assert.assertTrue(skip == 0);
        Assert.assertEquals(0xFF & testData[1023], memoryInputStream.read());
        Assert.assertEquals(-1, memoryInputStream.read());
        memoryInputStream.close();

    }

}

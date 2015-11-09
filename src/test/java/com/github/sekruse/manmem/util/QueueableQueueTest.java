package com.github.sekruse.manmem.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for the {@link QueueableQueue}.
 */
public class QueueableQueueTest {

    @Test
    public void testInitialQueueIsEmpty() {
        final QueueableQueue<TestQueueable> queue = new QueueableQueue<>();
        Assert.assertTrue(queue.isEmpty());
        Assert.assertNull(queue.poll());
        Assert.assertNull(queue.poll());
    }

    @Test
    public void testFifoOrder() {
        final QueueableQueue<TestQueueable> queue = new QueueableQueue<>();
        List<TestQueueable> queueables = Arrays.asList(new TestQueueable(), new TestQueueable(), new TestQueueable());
        for (TestQueueable queueable : queueables) {
            queue.add(queueable);
        }
        for (TestQueueable queueable : queueables) {
            final TestQueueable polled = queue.poll();
            Assert.assertSame(queueable, polled);
        }
        Assert.assertTrue(queue.isEmpty());
        Assert.assertNull(queue.poll());
    }



    /**
     * Minimal implementation of a {@link Queueable}.
     */
    private static class TestQueueable implements Queueable<TestQueueable> {

        private Queueable<TestQueueable> next, prev;

        @Override
        public void setPreviousElement(Queueable<TestQueueable> element) {
            this.prev = element;
        }

        @Override
        public void setNextElement(Queueable<TestQueueable> element) {
            this.next = element;
        }

        @Override
        public Queueable<TestQueueable> getNextElement() {
            return this.next;
        }

        @Override
        public Queueable<TestQueueable> getPreviousElement() {
            return this.prev;
        }
    }
}

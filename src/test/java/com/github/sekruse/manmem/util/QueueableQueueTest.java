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
        Assert.assertNull(queue.poll());
    }



    /**
     * Minimal implementation of a {@link Queueable}.
     */
    private static class TestQueueable implements Queueable<TestQueueable> {

        private Queueable<TestQueueable> next, prev;

        private QueueableQueue<TestQueueable> queue;

        public TestQueueable() {
            this(null);
        }

        public TestQueueable(QueueableQueue<TestQueueable> queue) {
            setQueue(queue);
        }

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

        @Override
        public QueueableQueue<TestQueueable> getQueue() {
            return this.queue;
        }

        @Override
        public void setQueue(QueueableQueue<TestQueueable> queue) {
            this.queue = queue;
        }
    }
}

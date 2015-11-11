package com.github.sekruse.manmem.util;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class makes queues made up from {@link Queueable} objects explicit by providing a head and a tail pointer and
 * several access methods.
 */
public class QueueableQueue<Element extends Queueable<Element>> {

    /**
     * The surrogate head element of the queue.
     */
    @SuppressWarnings("unchecked")
    private Queueable<Element> head = (Element) new Queueable<Element>() {

        private Queueable<Element> next;

        @Override
        public void setPreviousElement(Queueable<Element> element) {
            throw new RuntimeException("Must not access the previous element of a queue head.");
        }

        @Override
        public void setNextElement(Queueable<Element> element) {
            this.next = element;
        }

        @Override
        public Queueable<Element> getNextElement() {
            return this.next;
        }

        @Override
        public Queueable<Element> getPreviousElement() {
            throw new RuntimeException("Must not access the previous element of a queue head.");
        }

        @Override
        public Element reveal() {
            throw new RuntimeException("Cannot reveal a surrogate queue head.");
        }

        @Override
        public QueueableQueue<Element> getQueue() {
            return QueueableQueue.this;
        }

        @Override
        public void setQueue(QueueableQueue<Element> queue) {
            throw new RuntimeException("Method not supported.");
        }
    };

    /**
     * The surrogate tail element of the queue.
     */
    @SuppressWarnings("unchecked")
    private Queueable<Element> tail = new Queueable<Element>() {

        private Queueable<Element> prev;

        @Override
        public void setPreviousElement(Queueable<Element> element) {
            this.prev = element;
        }

        @Override
        public void setNextElement(Queueable<Element> element) {
            throw new RuntimeException("Must not access the next element of a queue tail.");
        }

        @Override
        public Queueable<Element> getNextElement() {
            throw new RuntimeException("Must not access the next element of a queue tail.");
        }

        @Override
        public Queueable<Element> getPreviousElement() {
            return this.prev;
        }

        @Override
        public Element reveal() {
            throw new RuntimeException("Cannot reveal a surrogate queue tail.");
        }

        @Override
        public QueueableQueue<Element> getQueue() {
            return QueueableQueue.this;
        }

        @Override
        public void setQueue(QueueableQueue<Element> queue) {
            throw new RuntimeException("Method not supported.");
        }

    };

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a new, empty queue.
     */
    public QueueableQueue() {
        // Bootstrapping: connect the head and tail surrogate elements.
        this.head.linkWithNextElement(this.tail);
    }

    /**
     * Adds an element to the tail of the queue.
     *
     * @param element the element to add
     */
    public void add(Queueable<Element> element) {
        getLock().lock();
        try {
            final Queueable<Element> secondToLastElement = this.tail.getPreviousElement();
            element.setQueue(this);
            secondToLastElement.linkWithNextElement(element);
            element.linkWithNextElement(this.tail);
        } finally {
            getLock().unlock();
        }
    }

    /**
     * Removes and returns the element at the head of the queue. Also, it unlinks this element.
     *
     * @return the removed element or {@code null} if no such element exists
     * @see Queueable#unlink()
     */
    public Element poll() {
        getLock().lock();
        try {
            final Queueable<Element> secondElement = this.head.getNextElement();
            if (secondElement == this.tail) {
                return null;
            }
            secondElement.notifyBeingPolled();
            secondElement.unlink();
            return secondElement.reveal();
        } finally {
            getLock().unlock();
        }

    }

    /**
     * Access to the queue's {@link Lock}. It should only be held very shortly to add or remove elements from the
     * queue.
     *
     * @return the {@link Lock} of this queue
     */
    public ReentrantLock getLock() {
        return this.lock;
    }
}

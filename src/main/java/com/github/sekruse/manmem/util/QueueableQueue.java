package com.github.sekruse.manmem.util;

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
    };

    /**
     * Creates a new, empty queue.
     */
    public QueueableQueue() {
        // Bootstrapping: connect the head and tail surrogate elements.
        this.head.linkWithNextElement(this.tail);
    }

    /**
     * @return whether this queue is empty
     */
    public boolean isEmpty() {
        return this.head.getNextElement() == this.tail;
    }

    /**
     * Adds an element to the tail of the queue.
     *
     * @param element the element to add
     */
    public void add(Queueable<Element> element) {
        final Queueable<Element> secondToLastElement = this.tail.getPreviousElement();
        secondToLastElement.linkWithNextElement(element);
        element.linkWithNextElement(this.tail);
    }

    /**
     * Removes and returns the element at the head of the queue. Also, it unlinks this element.
     *
     * @return the removed element or {@code null} if no such element exists
     * @see Queueable#unlink()
     */
    public Element poll() {
        if (this.isEmpty()) {
            return null;
        }

        final Queueable<Element> secondElement = this.head.getNextElement();
        this.head.linkWithNextElement(secondElement.getNextElement());
        secondElement.unlink();

        return secondElement.reveal();
    }

}

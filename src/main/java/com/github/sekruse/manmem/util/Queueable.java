package com.github.sekruse.manmem.util;

/**
 * This interface describes elements that can be put into a double-linked list. The elements form the double linked list
 * themselves and do not necessarily need a supporting data structure. However, if they are in a {@link QueueableQueue}
 * as defined by {@link #getQueue()}, then any modification to this object must only be done by the holder of
 * the {@link java.util.concurrent.locks.Lock} from {@link QueueableQueue#getLock()}. This is then of course valid
 * for all elements in the queue.
 *
 * @param <Self> should be the implementing class itself
 */
public interface Queueable<Self extends Queueable<Self>> {

    /**
     * This method allows to manipulate the queue that is implicitly defined by the {@link Queueable} interface.
     *
     * @param element the element that should precede this element
     */
    void setPreviousElement(Queueable<Self> element);

    /**
     * This method allows to manipulate the queue that is implicitly defined by the {@link Queueable} interface.
     *
     * @param element the element that should follow this element
     */
    void setNextElement(Queueable<Self> element);

    /**
     * Links this element with the given element as next element bi-directionally.
     *
     * @param element the element to link with
     */
    default void linkWithNextElement(Queueable<Self> element) {
        this.setNextElement(element);
        element.setPreviousElement(this);
    }

    /**
     * This method allows to navigate in the queue that is implicitly defined by the {@link Queueable} interface.
     *
     * @return the element that follows this element or {@code null} if no such element exists
     */
    Queueable<Self> getNextElement();

    /**
     * This method allows to navigate in the queue that is implicitly defined by the {@link Queueable} interface.
     *
     * @return the element that precedes this element or {@code null} if no such element exists
     */
    Queueable<Self> getPreviousElement();

    /**
     * Dequeues this element queuing up the next and previous elements and removing the links from this element.
     */
    default void unlink() {
        final Queueable<Self> nextElement = this.getNextElement();
        final Queueable<Self> prevElement = this.getPreviousElement();
        if (nextElement != null) {
            nextElement.setPreviousElement(prevElement);
        }
        if (prevElement != null) {
            prevElement.setNextElement(nextElement);
        }
        this.setNextElement(null);
        this.setPreviousElement(null);
    }

    /**
     * @return this objected, casted
     */
    default Self reveal() {
        return (Self) this;
    }

    /**
     * Returns the {@link QueueableQueue} in that this element is positioned.
     *
     * @return the containing queue or {@code null} if it is not in a queue
     */
    QueueableQueue<Self> getQueue();

    /**
     * Sets the {@link Queueable} of this element. Notice that for this operation, the calling thread should hold
     * both the {@link QueueableQueue#getLock()} of both the new and old {@link QueueableQueue}.
     *
     * @param queue the new {@link QueueableQueue}
     */
    void setQueue(QueueableQueue<Self> queue);

    /**
     * Try to lock the {@link QueueableQueue} as specified by {@link #getQueue()}. If there is none, the method will
     * always succeed.
     *
     * @return whether either the {@link java.util.concurrent.locks.Lock} could be obtained or {@link #getQueue()} is
     * {@code null}
     */
    default boolean tryLockQueue() {
        synchronized (this) {
            if (this.getQueue() == null) {
                return true;
            } else {
                return this.getQueue().getLock().tryLock();
            }
        }
    }

    /**
     * {@link QueueableQueue} calls this method to notify {@link Queueable} that they are about to be polled. Override
     * this method for clean-up. The {@link QueueableQueue#getLock()} should be held by the thread.
     */
    default void notifyBeingPolled() { }

}

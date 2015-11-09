package com.github.sekruse.manmem.util;

/**
 * This interface describes elements that can be put into a double-linked list. The elements form the double linked list
 * themselves and do not necessarily need a supporting data structure.
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

}

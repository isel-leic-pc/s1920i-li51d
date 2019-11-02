package pt.isel.pc.sketches.lockfree;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<E> {

    private final AtomicReference<Node<E>> head = new AtomicReference<>(null);

    public void push(E e) {
        Node<E> mynode = new Node<>(e);
        Node<E> observedHead;
        do {
            observedHead = head.get();
            mynode.next = observedHead;
        } while (!head.compareAndSet(observedHead, mynode));
        // exits the while when the CAS was successful
    }

    public Optional<E> pop() {
        Node<E> observedHead;
        Node<E> observedNext;
        do {
            observedHead = head.get();
            if (observedHead == null) {
                return Optional.empty();
            }
            // observedHead is not null
            observedNext = observedHead.next;
        } while (!head.compareAndSet(observedHead, observedNext));
        // Is this use of observedHead safe?
        return Optional.of(observedHead.value);
    }


    private static class Node<E> {
        final E value;
        Node<E> next;
        Node(E value) {
            this.value = value;
        }
    }
}

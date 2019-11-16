package pt.isel.pc.sketches.lockfree;

public class LockFreeQueue {

    // TODO

    private static class Node<E> {
        final E value;
        Node<E> next;
        Node(E value) {
            this.value = value;
        }
    }
}

package pt.isel.pc.sketches.lockfree;

public class LockFreeQueue {


    private static class Node<E> {
        final E value;
        LockFreeStack.Node<E> next;
        Node(E value) {
            this.value = value;
        }
    }
}

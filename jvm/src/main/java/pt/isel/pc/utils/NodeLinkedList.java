package pt.isel.pc.utils;

public class NodeLinkedList<T> {

    public static class Node<T> {
        public final T value;

        Node<T> next;
        Node<T> prev;

        Node(T value){
            this.value = value;
        }
    }

    private Node<T> head;

    public NodeLinkedList(){
        head = new Node<T>(null);
        head.next = head;
        head.prev = head;
    }

    public Node<T> push(T value) {
        Node<T> node = new Node<T>(value);
        Node<T> tail = head.prev;
        node.prev = tail;
        node.next = head;
        head.prev = node;
        tail.next = node;
        return node;
    }

    public boolean isEmpty() {
        return head == head.prev;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public T getHeadValue() {
        if(isEmpty()) {
            throw new IllegalStateException("cannot get head of an empty list");
        }
        return head.next.value;
    }

    public boolean isHeadNode(Node<T> node){
        return head.next == node;
    }

    public Node<T> pull () {
        if(isEmpty()) {
            throw new IllegalStateException("cannot pull from an empty list");
        }
        Node<T> node = head.next;
        head.next = node.next;
        node.next.prev = head;
        return node;
    }

    public void remove (Node<T> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}

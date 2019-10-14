package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReadersWriterLock {

    private final Lock monitor = new ReentrantLock();

    private boolean isWriting = false;
    private int nOfReaders = 0;

    private final NodeLinkedList<Request> wrQueue = new NodeLinkedList<>();
    private final Condition readCondition = monitor.newCondition();
    private final NodeLinkedList<MutableBoolean> rdSet = new NodeLinkedList<>();

    boolean startRead(long timeout, TimeUnit timeoutUnit)
      throws InterruptedException {

        try {
            monitor.lock();
            //fast-path
            if (!isWriting && wrQueue.isEmpty()) {
                nOfReaders += 1;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long deadline = Timeouts.start(timeout, timeoutUnit);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<MutableBoolean> myreq = rdSet.push(new MutableBoolean());
            while (true) {
                try {
                    readCondition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (myreq.value.booleanValue) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    rdSet.remove(myreq);
                    throw e;
                }
                if (myreq.value.booleanValue) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    rdSet.remove(myreq);
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }

    }

    boolean startWrite(long timeout, TimeUnit timeoutUnit)
      throws InterruptedException {
        try {
            monitor.lock();
            //fast-path
            if (!isWriting && nOfReaders == 0) {
                isWriting = true;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long deadline = Timeouts.start(timeout, timeoutUnit);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<Request> myreq = wrQueue.push(new Request(monitor));
            while (true) {
                try {
                    myreq.value.cond.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (myreq.value.isAllowed) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    wrQueue.remove(myreq);
                    if(wrQueue.isEmpty() && !isWriting) {
                        completeWaitingReaders();
                    }
                    throw e;
                }
                if (myreq.value.isAllowed) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    wrQueue.remove(myreq);
                    if(wrQueue.isEmpty() && !isWriting) {
                        completeWaitingReaders();
                    }
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }
    }

    void endRead() {
        try {
            monitor.lock();
            nOfReaders -= 1;
            if (nOfReaders == 0 && wrQueue.isNotEmpty()) {
                NodeLinkedList.Node<Request> writer = wrQueue.pull();
                writer.value.isAllowed = true;
                writer.value.cond.signal();
                isWriting = true;
            }
        } finally {
            monitor.unlock();
        }
    }

    void endWrite() {
        try {
            monitor.lock();
            isWriting = false;
            if (rdSet.isNotEmpty()) {
                completeWaitingReaders();
            } else if (wrQueue.isNotEmpty()) {
                NodeLinkedList.Node<Request> writer = wrQueue.pull();
                writer.value.isAllowed = true;
                writer.value.cond.signal();
                isWriting = true;
            }

        } finally {
            monitor.unlock();
        }
    }

    private void completeWaitingReaders() {
        while (rdSet.isNotEmpty()) {
            NodeLinkedList.Node<MutableBoolean> reader = rdSet.pull();
            reader.value.booleanValue = true;
            nOfReaders += 1;
        }
        readCondition.signalAll();
    }

    private static class Request {
        boolean isAllowed = false;
        Condition cond;

        Request(Lock lock) {
            cond = lock.newCondition();
        }
    }

    private static class MutableBoolean {
        boolean booleanValue = false;
    }

}

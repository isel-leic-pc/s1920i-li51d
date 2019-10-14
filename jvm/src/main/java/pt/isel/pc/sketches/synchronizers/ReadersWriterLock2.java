package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReadersWriterLock2 {

    private final Lock monitor = new ReentrantLock();

    private boolean isWriting = false;
    private int nOfReaders = 0;

    private final NodeLinkedList<Request> wrQueue = new NodeLinkedList<>();

    private final Condition readCondition = monitor.newCondition();
    private ReadRequest currentReadRequest = new ReadRequest();

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
            currentReadRequest.nOfReaders += 1;
            ReadRequest myreq = currentReadRequest;
            while (true) {
                try {
                    readCondition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (myreq.isAllowed) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    myreq.nOfReaders -= 1;
                    throw e;
                }
                if (myreq.isAllowed) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    myreq.nOfReaders -= 1;
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
            if (currentReadRequest.nOfReaders > 0) {
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
        if(currentReadRequest.nOfReaders > 0) {
            nOfReaders += currentReadRequest.nOfReaders;
            currentReadRequest.isAllowed = true;
            readCondition.signalAll();

            currentReadRequest.nOfReaders = 0;
        }
    }

    private static class Request {
        boolean isAllowed = false;
        Condition cond;

        Request(Lock lock) {
            cond = lock.newCondition();
        }
    }

    private static class ReadRequest {
        boolean isAllowed = false;
        int nOfReaders = 0;
    }

}

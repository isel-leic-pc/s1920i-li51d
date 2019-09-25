package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManualResetEvent {

    private static class Request {
        boolean isDone = false;
    }

    // ManualResetEvent can be in the "set" (true) or "reset" (false) state
    private boolean state;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    public ManualResetEvent(boolean initialState) {
        state = initialState;
    }

    public void reset() {
        monitor.lock();
        try{
            state = false;
        }finally {
            monitor.unlock();
        }
    }

    public void set() {
        monitor.lock();
        try{
            state = true;
            while(queue.isNotEmpty()) {
                NodeLinkedList.Node<Request> requestToComplete = queue.pull();
                requestToComplete.value.isDone = true;
            }
            condition.signalAll();
        }finally {
            monitor.unlock();
        }
    }

    // waits for the state to be set (i.e true)
    public boolean await(long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        monitor.lock();
        try{
            // happy path
            if(state == true) {
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            long limit = Timeouts.start(timeout, timeoutUnit);
            long remaining = Timeouts.remaining(limit);
            NodeLinkedList.Node<Request> node = queue.push(new Request());
            while (true) {
                try {
                    condition.await(remaining, TimeUnit.MILLISECONDS);
                }catch(InterruptedException e) {
                    queue.remove(node);
                    throw e;
                }

                // is condition true
                if (node.value.isDone) {
                    return true;
                }

                remaining = Timeouts.remaining(limit);
                // should we continue to wait
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
                    return false;
                }
            }
        }finally {
            monitor.unlock();
        }
    }

}

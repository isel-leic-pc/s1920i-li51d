using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace Examples
{
    /**
     * Semaphore with asynchronous Acquire operation, supporting timeout and cancellation via CancellationToken
     */
    public class AsyncSemaphore
    {
        private static readonly Task<bool> TrueTask = Task.FromResult(true);
        private static readonly Task<bool> FalseTask = Task.FromResult(false);
        
        // Object used for mutual exclusion while accessing shared data  
        private readonly object _mon = new object();
        
        private int _units;
        private readonly LinkedList<Consumer> _consumers = new LinkedList<Consumer>();

        // for debugging purposes, not required
        private readonly Counter _successRace;
        private readonly Counter _timeoutRace;
        private readonly Counter _cancelRace;
        private readonly Counter _reentrancyCounter;
        private readonly ThreadLocal<int> _tls = new ThreadLocal<int>(() => 0);

        private class Consumer : ConsumerBase<bool>
        {
            public int Units { get; }

            public Consumer(int units)
            {
                Units = units;
            }

            public Timer Timer { get; set; }
            public CancellationTokenRegistration CancellationRegistration { get; set; }
        }

        public AsyncSemaphore(
            int maxUnits,
            Counter successRace, Counter timeoutRace, Counter cancelRace, Counter reentrancyCounter)
        {
            _units = maxUnits;
            _successRace = successRace;
            _timeoutRace = timeoutRace;
            _cancelRace = cancelRace;
            _reentrancyCounter = reentrancyCounter;
        }

        public Task<bool> Acquire(int requestedUnits, TimeSpan timeout, CancellationToken ct)
        {
            try
            {
                // for debugging
                _tls.Value += 1;
                _reentrancyCounter.Ceiling(_tls.Value);
                lock (_mon)
                {
                    if (_consumers.Count == 0 && _units >= requestedUnits)
                    {
                        _units -= requestedUnits;
                        return TrueTask;
                    }

                    if (timeout.TotalMilliseconds <= 0)
                    {
                        return FalseTask;
                    }

                    var consumer = new Consumer(requestedUnits);
                    var node = _consumers.AddLast(consumer);

                    consumer.Timer = new Timer(CancelDueToTimeout, node, timeout, new TimeSpan(-1));
                    consumer.CancellationRegistration = ct.Register(CancelDueToCancellationToken, node);

                    return consumer.Task;
                }
            }
            finally
            {
                _tls.Value -= 1;
            }
        }

        private void CancelDueToTimeout(object state)
        {
            var node = (LinkedListNode<Consumer>) state;

            if (!node.Value.TryAcquire())
            {
                // bailing out, some other thread is already dealing with this node
                _timeoutRace.Increment();
                return;
            }

            List<Consumer> consumersToComplete;
            lock (_mon)
            {
                node.Value.CancellationRegistration.Dispose();
                _consumers.Remove(node);
                // Because a cancellation can complete other consumers
                consumersToComplete = ReleaseAllWithinLock();
            }

            // Complete tasks *outside* the lock
            node.Value.SetResult(false);
            foreach (var consumer in consumersToComplete)
            {
                consumer.SetResult(true);
            }
        }

        private void CancelDueToCancellationToken(object state)
        {
            var node = (LinkedListNode<Consumer>) state;

            if (!node.Value.TryAcquire())
            {
                // bailing out, some other thread is already dealing with this node
                _cancelRace.Increment();
                return;
            }

            node.Value.Timer.Dispose();
            List<Consumer> consumersToComplete;
            lock (_mon)
            {
                _consumers.Remove(node);
                // Because a cancellation can complete other consumers
                consumersToComplete = ReleaseAllWithinLock();
            }

            // Complete tasks *outside* the lock
            node.Value.SetCanceled();
            foreach (var consumer in consumersToComplete)
            {
                consumer.SetResult(true);
            }
        }

        public void Release(int releaseUnits)
        {
            try
            {
                _tls.Value += 1;
                _reentrancyCounter.Ceiling(_tls.Value);

                List<Consumer> consumersToComplete;
                lock (_mon)
                {
                    _units += releaseUnits;
                    consumersToComplete = ReleaseAllWithinLock();
                }

                // Complete tasks *outside* the lock
                foreach (var consumer in consumersToComplete)
                {
                    consumer.SetResult(true);
                }
            }
            finally
            {
                _tls.Value -= 1;
            }
        }

        /**
         * Goes through the consumer list and removes all requests that can be fulfilled by the units.
         * Returns a list with them, so that the task can be completed outside the lock
         * It must always be called with the lock already acquired.
         */
        private List<Consumer> ReleaseAllWithinLock()
        {
            var consumersToComplete = new List<Consumer>();
            while (_consumers.Count() != 0 && _units >= _consumers.First.Value.Units)
            {
                var first = _consumers.First;
                if (!first.Value.TryAcquire())
                {
                    // Some other thread is already dealing with this node, 
                    // so bail out of this. That other thread will remove this node and
                    // perform the required processing.
                    _successRace.Increment();
                    break;
                }

                _consumers.RemoveFirst();
                first.Value.Timer.Dispose();
                first.Value.CancellationRegistration.Dispose();
                _units -= first.Value.Units;
                consumersToComplete.Add(first.Value);
            }

            return consumersToComplete;
        }
    }
}

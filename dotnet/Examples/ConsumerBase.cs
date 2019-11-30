using System.Threading;
using System.Threading.Tasks;

namespace Examples
{
    /**
     * Base class for consumer nodes used on asynchronous operation
     * Each consumer has a TaskCompletionSource:
     * - Used to obtain the Task associated to the pending operation
     * - Used by completion code to complete this Task
     */
    public class ConsumerBase<T> : TaskCompletionSource<T>
    {
        private int _acquired = 0;

        /**
         * This method provides a lock-free way for the completion thread to
         * acquire ownership for the completion of the associated task.
         * Note that a consumer can be concurrently completed by multiple threads
         * (e.g. success completion happening "at the same time" as the timeout callback)
         * Only the thread the successfully "acquires" the node should continue with the completion procedure.
         */
        public bool TryAcquire()
        {
            return Interlocked.CompareExchange(ref _acquired, 1, 0) == 0;
        }
    }
}
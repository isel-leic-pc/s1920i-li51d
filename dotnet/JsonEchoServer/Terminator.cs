using System;
using System.Threading;
using System.Threading.Tasks;

namespace JsonEchoServer
{
    class Terminator
    {
        private int counter;
        private volatile bool isShutdown;
        private readonly TaskCompletionSource<object> tcs = new TaskCompletionSource<object>();

        public IDisposable Enter()
        {
            // This needs to be here to avoid races
            Interlocked.Increment(ref counter);
            Interlocked.MemoryBarrier();
            if (isShutdown)
            {
                Leave();
                throw new Exception("cannot enter because it is shutting down");
            }

            return new TerminatorDisposable(this);
        }

        private void Leave()
        {
            Interlocked.Decrement(ref counter);
            Interlocked.MemoryBarrier();
            if (isShutdown)
            {
                tcs.TrySetResult(null);
            }
        }

        public Task Shutdown()
        {
            isShutdown = true;
            Interlocked.MemoryBarrier();
            if (counter == 0)
            {
                tcs.TrySetResult(null);
            }

            return tcs.Task;
        }

        class TerminatorDisposable : IDisposable
        {
            private readonly Terminator terminator;

            public TerminatorDisposable(Terminator terminator)
            {
                this.terminator = terminator;
            }

            public void Dispose()
            {
                terminator.Leave();
            }
        }
    }
}
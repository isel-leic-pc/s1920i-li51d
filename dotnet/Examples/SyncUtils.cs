using System;
using System.Threading;

namespace Examples
{
    public class SyncUtils
    {
        public static void Wait(object mlock, object condition, int timeout)
        {
            if (mlock == condition)
            {
                Monitor.Wait(mlock, timeout);
            }

            Monitor.Enter(condition);
            Monitor.Exit(mlock);
            try
            {
                Monitor.Wait(condition, timeout);
            }
            finally
            {
                Monitor.Exit(condition);
                EnterUninterruptible(mlock, out var wasInterrupted);
                if (wasInterrupted)
                {
                    throw new ThreadInterruptedException();
                }
            }
        }

        public static void Notify(object mlock, object condition)
        {
            if (mlock == condition)
            {
                Monitor.Pulse(mlock);
                return;
            }

            EnterUninterruptible(condition, out bool wasInterrupted);
            Monitor.Pulse(condition);
            Monitor.Exit(condition);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void NotifyAll(object mlock, object condition)
        {
            if (mlock == condition)
            {
                Monitor.Pulse(mlock);
                return;
            }

            EnterUninterruptible(condition, out bool wasInterrupted);
            Monitor.Pulse(condition);
            Monitor.Exit(condition);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void EnterUninterruptible(object mon, out bool wasInterrupted)
        {
            wasInterrupted = false;
            while (true)
            {
                try
                {
                    Monitor.Enter(mon);
                    return;
                }
                catch (ThreadInterruptedException)
                {
                    wasInterrupted = true;
                }
            }
        }
    }
}
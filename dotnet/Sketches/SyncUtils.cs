using System;
using System.Threading;

namespace Sketches
{
    public static class MonitorUtils
    {
        public static bool Wait(object monitor, object condition, int timeoutInMillis)
        {
            if (monitor == condition)
            {
                return Monitor.Wait(monitor, timeoutInMillis);
            }
            // Wait must be called while inside `monitor`
            Monitor.Enter(condition);
            Monitor.Exit(monitor);
            try
            {
                return Monitor.Wait(condition, timeoutInMillis);
            }
            finally
            {
                Monitor.Exit(condition);
                MonitorUtils.EnterUninterruptible(monitor, out var wasInterrupted);
                if (wasInterrupted)
                {
                    throw new ThreadInterruptedException();
                }
            }
            // Wait returns while inside `monitor`!
        }

        public static void Pulse(object monitor, object condition)
        {
            if (monitor == condition)
            {
                Monitor.Pulse(monitor);
                return;
            }
            MonitorUtils.EnterUninterruptible(condition, out var wasInterrupted);
            Monitor.Pulse(condition);
            Monitor.Exit(condition);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void EnterUninterruptible(object monitor, out bool wasInterrupted)
        {
            wasInterrupted = false;
            while (true)
            {
                try
                {
                    Monitor.Enter(monitor);
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
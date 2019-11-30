using System;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace Examples
{
    public static class TestAsyncSemaphore
    {
        private static readonly Counter SuccessCounter = new Counter("success");
        private static readonly Counter TimeoutCounter = new Counter("timeout");
        private static readonly Counter CancellationCounter = new Counter("cancellation");

        private static readonly Counter SuccessRace = new Counter("successRace");
        private static readonly Counter TimeoutRace = new Counter("timeoutRace");
        private static readonly Counter CancelRace = new Counter("cancelRace");
        private static readonly Counter ReentrancyCounter = new Counter("reentrancy");

        private static readonly Counter[] Counters =
        {
            SuccessCounter, TimeoutCounter, CancellationCounter,
            SuccessRace, TimeoutRace, CancelRace, ReentrancyCounter
        };


        private const int MaxUnits = 3;

        private static readonly AsyncSemaphore Semaphore =
            new AsyncSemaphore(MaxUnits, SuccessRace, TimeoutRace, CancelRace, ReentrancyCounter);

        private static int _grantedUnits = 0;
        private const int NOfFlows = 100;

        public static void Run()
        {
            Console.WriteLine("# Running AsynchronousSemaphore tests #");
            for (var i = 0; i < NOfFlows; ++i)
            {
                Task.Factory.StartNew(Flow);
            }

            for (;;)
            {
                Thread.Sleep(1000);
                Console.WriteLine(Counters
                    .Select(c => c.ToString())
                    .Aggregate((s1, s2) => s1 + s2));
            }
        }

        private static async void Flow()
        {
            var r = new Random();
            for (;;)
            {
                var units = (r.Next() % (MaxUnits - 1)) + 1;

                var timeoutTime = TimeSpan.FromMilliseconds(r.Next() % 100);
                var cancellationTime = r.Next() % 100;
                var cts = new CancellationTokenSource();
                cts.CancelAfter(cancellationTime);
                try
                {
                    if (r.Next() % 5 == 0)
                    {
                        // for 1 in N, the token is already cancelled when entering the Acquire
                        cts.Cancel();
                    }

                    var result = await Semaphore.Acquire(units, timeoutTime, cts.Token);
                    if (result == false)
                    {
                        TimeoutCounter.Increment();
                        continue;
                    }

                    SuccessCounter.Increment();
                    var currentlyGranted = Interlocked.Add(ref _grantedUnits, units);
                    if (currentlyGranted > MaxUnits)
                    {
                        Console.WriteLine($"ERROR: granted {currentlyGranted} and max is {MaxUnits}");
                        Environment.Exit(1);
                    }

                    if (r.Next() % 2 == 0)
                    {
                        await Task.Delay(r.Next() % 10);
                    }

                    Interlocked.Add(ref _grantedUnits, -units);
                    Semaphore.Release(units);
                }
                catch (Exception e)
                {
                    CancellationCounter.Increment();
                }
            }
        }
    }
}
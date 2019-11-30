using System.Threading;

namespace Examples
{
    public class Counter
    {
        private readonly string _name;
        private volatile int _counter;

        public Counter(string name)
        {
            this._name = name;
        }

        public void Increment()
        {
            Interlocked.Increment(ref _counter);
        }

        public void Ceiling(int value)
        {
            do
            {
                var observed = _counter;
                if (value <= observed) return;
                if (Interlocked.CompareExchange(ref _counter, value, observed) == observed)
                {
                    return;
                }
            } while (true);
        }

        public override string ToString()
        {
            return $" {_name}: {_counter}";
        }
    }
}
public class SwapDeadlock {
    private int val;
    public SwapDeadlock(int val) {
        this.val = val;
    }
    public synchronized void swapVal(SwapDeadlock other) {
        synchronized(other) {
            int tmp = this.val;
            this.val = other.val;
            other.val = tmp;
        }
    }
    public synchronized void swapVal2(SwapDeadlock other) {
        synchronized(other) {
            int tmp = this.val;
            this.val = other.val;
            other.val = tmp;
        }
    }
}

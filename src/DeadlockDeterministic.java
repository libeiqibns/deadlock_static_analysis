public class DeadlockDeterministic {
    private static final int NUM_THREADS = 50;
    private static final Object[] locks = new Object[NUM_THREADS];

    // Initialize the lock objects.
    static {
        for (int i = 0; i < NUM_THREADS; i++) {
            locks[i] = new Object();
        }
    }

    public static void main(String[] args) {
        Thread[] threads = new Thread[NUM_THREADS];

        // Create and start threads.
        for (int i = 0; i < NUM_THREADS; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                int next = (index + 1) % NUM_THREADS;
                synchronized (locks[index]) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    synchronized (locks[next]) {
                        System.out.println("Thread " + index + " acquired locks " + index + " and " + next);
                    }
                }
            }, "Thread-" + i);
            threads[i].start();
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
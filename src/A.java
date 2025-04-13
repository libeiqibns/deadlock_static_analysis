public class A {
    class B {

    }
    class C {

    }
    B lock;
    public void foo(B b1, C c1) {
        synchronized(b1) {
            // some statements
            synchronized (c1) {
                // some statements
                synchronized (this) {
                    // some statements
                }
            }
        }
    }
    
    public synchronized void bar(B b2, C c2) throws InterruptedException {
        if (true) {
            synchronized(b2) {
                // some statements
                foo(b2, c2);
            }
        } else {
            synchronized(c2) {
                // some statements
                c2.wait(  );
            }
        }
    }
}

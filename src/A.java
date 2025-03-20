public class A {
    class B {

    }
    class C {

    }
    B lock;
    public void foo(B b1, C c1) {
        synchronized(b1) {
            synchronized (c1) {
                synchronized (this) {
                    
                }
            }
        }
    }
    
    public synchronized void bar(B b2, C c2) {
        synchronized(b2) {

        }
        synchronized(c2) {

        }
    }
}

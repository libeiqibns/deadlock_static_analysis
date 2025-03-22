public class A {
    class B {

    }
    class C {

    }
    B lock;
    public void foo(B b1, C c1) {
        synchronized(b1) {
            System.out.println("");
            synchronized (c1) {
                System.out.println("");
                synchronized (this) {
                    System.out.println("");
                }
            }
        }
    }
    
    public synchronized void bar(B b2, C c2) {
        synchronized(b2) {
            System.out.println("");
        }
        synchronized(c2) {
            System.out.println("");
        }
    }
}

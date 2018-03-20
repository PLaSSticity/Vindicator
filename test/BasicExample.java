package test;

public class BasicExample {

    static int x = 0;
    static final Object m = new Object();

    public static class Thread1 extends Thread implements Runnable {
        public int t;
        public void run() {
            synchronized (m) {
                t = x;
            }
        }
    }

    public static class Thread2 extends Thread implements Runnable {
        public int t;
        public void run() {
            x = 0;
        }
    }

    public static void main(String args[]) throws Exception {
        final Thread1 t1 = new Thread1();
        final Thread2 t2 = new Thread2();

        t1.start();
        t2.start();

        t1.join();
        t2.join();
    }
}

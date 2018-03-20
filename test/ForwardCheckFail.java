package test;

public class ForwardCheckFail {

    static int count = 0;
    static int lastLink = 0;
    static int notifyPeriod = 0;
    static int numPassed = 0;
    static int lastBit = 0;
    static int transmissions = 0;
    static int next = 0;

    static final Object l0 = new Object();
    static final Object l1 = new Object();

    static int f = 0;
    static int o = 0;
    static final Object oLock = new Object();
    static final Object mLock = new Object();


    public static class Thread1 extends Thread implements Runnable {
        public int t;
        public void run() {
            // ------------[  0 @ 0  ]------------
            synchronized (l1) {
                t = transmissions;
                try{Thread.sleep(400);}catch(Exception e){}
                // ------------[  2 @ 400  ]------------
            }
            t = count;
            synchronized (l0) {
                t = lastLink;
            }
            t = notifyPeriod;
            synchronized (l0) {
                t = lastLink;
                numPassed = 1;
            }
            t = notifyPeriod;
            synchronized (l0) {
                t = lastLink;
            }

            try{Thread.sleep(400);}catch(Exception e){}
            // ------------[  4 @ 800  ]------------
            synchronized (l0) {
                t = lastLink;
                numPassed = 2;
            }
            synchronized (l1) {
                t = transmissions;
            }
        }
    }

    public static class Thread2 extends Thread implements Runnable {
        public int t;
        public void run() {
            try{Thread.sleep(200);}catch(Exception e){}
            // ------------[  1 @ 200  ]------------
            lastBit = 1;

            try{Thread.sleep(400);}catch(Exception e){}
            // ------------[  3 @ 600  ]------------
            synchronized (l0) {
                t = numPassed;
            }
            synchronized (l1) {
                t = transmissions;
                t = lastBit;
            }
        }
    }

    public static class Thread3 extends Thread implements Runnable {
        public int t;
        public void run() {
            try{Thread.sleep(600);}catch(Exception e){}
            // ------------[  3 @ 600  ]------------
            synchronized (l1) {
                t = lastBit;
            }
            t = count;
            synchronized (l0) {
                t = lastLink;
            }
            synchronized (l0) {
                t = numPassed;
            }
            synchronized (l1) {
                t = transmissions;
            }

            t = notifyPeriod;
            synchronized (l0) {
                t = lastLink;
                t = next;
                t = numPassed;
                numPassed = 3;
            }
            t = notifyPeriod;
            try{Thread.sleep(400);}catch(Exception e){}
            // ------------[  5 @ 1000  ]------------
            lastBit = 4;
        }
    }

    public static void main(String args[]) throws Exception {
        final Thread1 t1 = new Thread1();
        final Thread2 t2 = new Thread2();
        final Thread3 t3 = new Thread3();

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();
    }
}

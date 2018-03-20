package test;

/*  T1    T2    T3    T4
   ----  ----  ----  ----
   a(m)     part that causes random reordering to get stuck
   a(n)
   wr(x)
   r(n)
   r(m)
         a(p)
         a(n)
         rd(x)
         r(n)
         r(p)
               a(p)
               a(q)
               wr(y)
               r(q)
               r(p)
                     a(m)
                     a(q)
                     rd(y)
                     r(q)
                     r(m)
                                part that causes a WDC race to trigger vindication
                     wr(z)
                     sync(o)
               sync(o)
               a(b)
               r(b)
         a(b)
         r(b)
         rd(z)
   */


public class CycleStuckRandomReorderSimplified {
    // Locks/vars that get random reordering stuck
    static int x = 0;
    static int y = 0;
    static final Object m = new Object();
    static final Object n = new Object();
    static final Object p = new Object();
    static final Object q = new Object();

    // Locks/vars to cause a WDC race, so that vindication is triggered
    static int z = 0;
    static int o = 0;
    static final Object oLock = new Object();
    static final Object b = new Object();


    public static class Thread1 extends Thread implements Runnable {
        public int t;
        public void run() {
            // ------------[  0 @ 0  ]------------
            synchronized (m) {
                synchronized (n) {
                    x = 1;
                }
            }

        }
    }

    public static class Thread2 extends Thread implements Runnable {
        public int t;
        public void run() {
            try{Thread.sleep(200);}catch(Exception e){}
            // ------------[  1 @ 200  ]------------
            synchronized (p) {
                synchronized (n) {
                    t = x;
                }
            }

            try{Thread.sleep(800);}catch(Exception e){}
            // ------------[  5 @ 1000  ]------------
            synchronized (b) {}
            t = z;
        }
    }

    public static class Thread3 extends Thread implements Runnable {
        public int t;
        public void run() {
            try{Thread.sleep(400);}catch(Exception e){}
            // ------------[  2 @ 400  ]------------
            synchronized (p) {
                synchronized (q) {
                    y = 1;
                }
            }

            try{Thread.sleep(400);}catch(Exception e){}
            // ------------[  4 @ 800  ]------------
            synchronized (oLock) { o++; }
            synchronized (b) {}
        }
    }

    public static class Thread4 extends Thread implements Runnable {
        public int t;
        public void run() {
            try{Thread.sleep(600);}catch(Exception e){}
            // ------------[  3 @ 600  ]------------
            synchronized (m) {
                synchronized (q) {
                    t = y;
                }
            }

            z = 1;
            synchronized (oLock) { o++; }
        }
    }

    public static void main(String args[]) throws Exception {
        final Thread1 t1 = new Thread1();
        final Thread2 t2 = new Thread2();
        final Thread3 t3 = new Thread3();
        final Thread4 t4 = new Thread4();

        t1.start();
        t2.start();
        t3.start();
        t4.start();

        t1.join();
        t2.join();
        t3.join();
        t4.join();
    }
}

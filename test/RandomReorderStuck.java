package test;

public class RandomReorderStuck {

	/* First, we cause several races that are correctly caught. Then,
	 * we cause a DC race after those to trigger a reordering that
	 * includes the edges added by the previously detected races.
	 */
	
	static int x = 0;
	static final Object m = new Object();

	static int o = 0;
	static final Object oLock = new Object();
	

	public static class Thread1 extends Thread implements Runnable {
		public int t;
		public void run() {
			try{Thread.sleep(600);}catch(Exception e){}
			// ------------[  3 @ 600  ]------------
			synchronized(m) {
				t = x;
			}

			try{Thread.sleep(400);}catch(Exception e){}
			// ------------[  5 @ 1000  ]------------
			synchronized(oLock) { o = o + 1; } // sync(o)
			synchronized(m) {}
		}
	}

	public static class Thread2 extends Thread implements Runnable {
		public int t;
		public void run() {
			// ------------[  0 @ 0  ]------------
			synchronized(m) {
				t = x;
				try{Thread.sleep(200);}catch(Exception e){}
				// ------------[  1 @ 200  ]------------
			}
			try{Thread.sleep(600);}catch(Exception e){}
			// ------------[  4 @ 800  ]------------
			synchronized(m) {
				t = x;
			}

			synchronized(oLock) { o = o + 1; } // sync(o)
		}
	}

	public static class Thread3 extends Thread implements Runnable {
		public int t;
		public void run() {
			try{Thread.sleep(400);}catch(Exception e){}
			// ------------[  2 @ 400  ]------------
			x = 1;
			
			try{Thread.sleep(800);}catch(Exception e){}
			// ------------[  6 @ 1200  ]------------
			synchronized(m) {}
			x = 3;
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

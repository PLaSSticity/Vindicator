package test;
public class GeneratedExample extends Thread {

	static int Var0;
	static int Var1;
	static int Var2;
	static int Var3;
	static int Var4;
	static final Object L0 = new Object();
	static final Object L1 = new Object();
	static final Object L2 = new Object();
	static final Object L3 = new Object();
	static final Object L4 = new Object();
	static final Object L5 = new Object();
	static final Object L6 = new Object();
	static final Object L7 = new Object();
	static final Object L8 = new Object();
	static int L0Var;
	static int L1Var;
	static int L2Var;
	static int L3Var;
	static int L4Var;
	static int L5Var;
	static int L6Var;
	static int L7Var;
	static int L8Var;
	static void sync(Object lock) {
		synchronized (lock) {
			if (lock == L0) L0Var = 1;
			else if (lock == L1) L1Var = 1;
			else if (lock == L2) L2Var = 1;
			else if (lock == L3) L3Var = 1;
			else if (lock == L4) L4Var = 1;
			else if (lock == L5) L5Var = 1;
			else if (lock == L6) L6Var = 1;
			else if (lock == L7) L7Var = 1;
			else if (lock == L8) L8Var = 1;
			else throw new RuntimeException();
		}
	}
	static void sleepSec(float sec) {
		try {
			Thread.sleep((long)(sec * 500));
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public void run() {
		sleepSec(3);
		synchronized(L1) {
		sync(L7);
		synchronized(L4) {
		}
		sync(L5);
		sync(L8);
		Var0 = 1;
		}
	}
	public static class Test1 extends Thread implements Runnable {
		public void run() {
		sleepSec(1);
		synchronized(L4) {
		synchronized(L0) {
		sync(L5);
		}
		sync(L3);
		synchronized(L6) {
		}
		}
		}
	}
	public static class Test2 extends Thread implements Runnable {
		public void run() {
		synchronized(L0) {
		Var0 = 1;
		}
		synchronized(L1) {
		synchronized(L2) {
		}
		sync(L3);
		}
		}
	}
	public static class Test3 extends Thread implements Runnable {
		public void run() {
		sleepSec(2);
		synchronized(L6) {
		synchronized(L2) {
		sync(L7);
		}
		sync(L8);
		}
		}
	}
	public static void main(String args[]) throws Exception {
		final GeneratedExample t0 = new GeneratedExample();
		final Test1 t1 = new Test1();
		final Test2 t2 = new Test2();
		final Test3 t3 = new Test3();
		t0.start();
		t1.start();
		t2.start();
		t3.start();
		t0.join();
		t1.join();
		t2.join();
		t3.join();
	}
}
/******************************************************************************

Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.  

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

******************************************************************************/

package test;

public class acyclicNonPredictableRace extends Thread {

	static int x;
	static final Object o = new Object();
	static final Object m = new Object();
	static final Object p = new Object();
	
	static int o1m1Var;
	static int o1m2Var;
	static int m1p1Var;
	static int p1m1Var;
	static int p1o2Var;
	static int m2p1Var;
	static int p2o2Var;
	static int p2m2Var;
	static int m2p2Var;
	static int o2rdxVar;
	static final Object o1m1 = new Object();
	static final Object o1m2 = new Object();
	static final Object m1p1 = new Object();
	static final Object p1m1 = new Object();
	static final Object p1o2 = new Object();
	static final Object m2p1 = new Object();
	static final Object p2o2 = new Object();
	static final Object p2m2 = new Object();
	static final Object m2p2 = new Object();
	static final Object o2rdx = new Object();

	// Predictable race: x
	// WDC-race: x
	// WCP-race: ?
	// CP-race: ?

	static void sync(Object lock) {
		synchronized (lock) {
			if (lock == o1m1) o1m1Var = 1;
			else if (lock == o1m2) o1m2Var = 1;
			else if (lock == m1p1) m1p1Var = 1;
			else if (lock == p1m1) p1m1Var = 1;
			else if (lock == p1o2) p1o2Var = 1;
			else if (lock == m2p1) m2p1Var = 1;
			else if (lock == p2o2) p2o2Var = 1;
			else if (lock == p2m2) p2m2Var = 1;
			else if (lock == m2p2) m2p2Var = 1;
			else if (lock == o2rdx) o2rdxVar = 1;
			else throw new RuntimeException();
		}
	}
	
	static void sleepSec(float sec) {
		try{
			Thread.sleep((long)(sec * 1000));
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void run() {
		synchronized(o) {
			sync(o1m1);
			sync(o1m2);
			x = 1;
		}
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			synchronized(m) {
				sync(m1p1);
				sleepSec(3);
				sync(p1m1);
				sync(o1m1);
			}
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			sleepSec(2);
			synchronized(p) {
				sync(p1o2);
				sync(p1m1);
				sync(m1p1);
				sleepSec(5);
				sync(m2p1);
			}
		}
	}
	
	public static class Test4 extends Thread implements Runnable {
		public void run() {
			sleepSec(4);
			synchronized(m) {
				sync(m2p1);
				sync(m2p2);
				sleepSec(7);
				sync(p2m2);
				sync(o1m2);
			}
		}
	}
	
	public static class Test5 extends Thread implements Runnable {
		public void run() {
			sleepSec(6);
			synchronized(p) {
				sync(p2o2);
				sync(p2m2);
				sleepSec(8);
				sync(m2p2);
			}
		}
	}
	
	public static class Test6 extends Thread implements Runnable {
		public void run() {
			sleepSec(9);
			synchronized(o) {
				sync(o2rdx);
				sync(p1o2);
				sync(p2o2);
			}
		}
	}
	
	public static class Test7 extends Thread implements Runnable {
		public void run() {
			sleepSec(10);
			sync(o2rdx);
			int t = x;
		}
	}

	public static void main(String args[]) throws Exception {
		final acyclicNonPredictableRace t1 = new acyclicNonPredictableRace();
		final Test2 t2 = new Test2();
		final Test3 t3 = new Test3();
		final Test4 t4 = new Test4();
		final Test5 t5 = new Test5();
		final Test6 t6 = new Test6();
		final Test7 t7 = new Test7();
		t1.start();
		t2.start();
		t3.start();
		t4.start();
		t5.start();
		t6.start();
		t7.start();
		t1.join();
		t2.join();
		t3.join();
		t4.join();
		t5.join();
		t6.join();
		t7.join();
	}
}
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

public class WDCFlip extends Thread {

	static int x;
	static final Object l = new Object();
	static final Object m = new Object();
	static final Object n = new Object();
	
	static int hVar;
	static int oVar;
	static int pVar;
	static int qVar;
	static int rVar;
	static int sVar;
	static int tVar;
	static final Object h = new Object();
	static final Object o = new Object();
	static final Object p = new Object();
	static final Object q = new Object();
	static final Object r = new Object();
	static final Object s = new Object();
	static final Object t = new Object();

	// Predictable race: ??
	// WDC-race: x
	// WCP-race: none
	// CP-race: none

	static void sync(Object lock) {
		synchronized (lock) {
			if (lock == h) hVar = 1;
			else if (lock == o) oVar = 1;
			else if (lock == p) pVar = 1;
			else if (lock == q) qVar = 1;
			else if (lock == r) rVar = 1;
			else if (lock == s) sVar = 1;
			else if (lock == t) tVar = 1;
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
		sleepSec(4);
		sync(o); System.out.println("T1: sync o");
		x = 1; System.out.println("T1: wr x");
		sync(p); System.out.println("T1: sync p");
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			sleepSec(3);
			synchronized(m) { System.out.println("T2: acq m");
				sync(o); System.out.println("T2: sync o");
				sleepSec(2);
				sync(p); System.out.println("T2: sync p");
			} System.out.println("T2: rel m");
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			synchronized(m) { System.out.println("T3: acq m");
				sync(q); System.out.println("T3: sync q");
				sleepSec(2);
				sync(r); System.out.println("T3: sync r");
			} System.out.println("T3: rel m");
		}
	}
	
	public static class Test4 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			sync(q); System.out.println("T4: sync q");
			x = 2; System.out.println("T4: wr x");
			sync(r); System.out.println("T4: sync r");
		}
	}
	
	public static void main(String args[]) throws Exception {
		final WDCFlip t1 = new WDCFlip();
		final Test2 t2 = new Test2();
		final Test3 t3 = new Test3();
		final Test4 t4 = new Test4();
		t1.start();
		t2.start();
		t3.start();
		t4.start();
		t1.join();
		t2.join();
		t3.join();
		t4.join();
		System.out.println("x = " + x);
	}
}
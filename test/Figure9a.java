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

public class Figure9a extends Thread {

	static int x;
	static final Object o = new Object();
	static final Object m = new Object();
	static final Object p = new Object();
	
	static int s1Var;
	static int s2Var;
	static int s3Var;
	static int s4Var;
	static int s5Var;
	static int s6Var;
	static int s7Var;
	static int s8Var;
	static int s9Var;
	static int s10Var;
	static int s11Var;
	static int s12Var;
	static int s13Var;
	static int s14Var;
	static final Object s1 = new Object();
	static final Object s2 = new Object();
	static final Object s3 = new Object();
	static final Object s4 = new Object();
	static final Object s5 = new Object();
	static final Object s6 = new Object();
	static final Object s7 = new Object();
	static final Object s8 = new Object();
	static final Object s9 = new Object();
	static final Object s10 = new Object();
	static final Object s11 = new Object();
	static final Object s12 = new Object();
	static final Object s13 = new Object();
	static final Object s14 = new Object();

	// Predictable race: x
	// DC-race: x

	static void sync(Object lock) {
		synchronized (lock) {
			if (lock == s1) s1Var = 1;
			else if (lock == s2) s2Var = 1;
			else if (lock == s3) s3Var = 1;
			else if (lock == s4) s4Var = 1;
			else if (lock == s5) s5Var = 1;
			else if (lock == s6) s6Var = 1;
			else if (lock == s7) s7Var = 1;
			else if (lock == s8) s8Var = 1;
			else if (lock == s9) s9Var = 1;
			else if (lock == s10) s10Var = 1;
			else if (lock == s11) s11Var = 1;
			else if (lock == s12) s12Var = 1;
			else if (lock == s13) s13Var = 1;
			else if (lock == s14) s14Var = 1;
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
			sync(s1);
			sleepSec(20);
			sync(s11);
			sync(s12);
			sync(s13);
			sync(s14);
			x = 1;
		}
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			synchronized(m) {
				sync(s2);
				sync(s3);
				sleepSec(3);
				sync(s1);
				sync(s5);
			}
			sync(s11);
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			sleepSec(2);
			synchronized(p) {
				sync(s4);
				sync(s5);
				sleepSec(5);
				sync(s2);
//				sync(s6);
			}
			sync(s12);
		}
	}
	
	public static class Test4 extends Thread implements Runnable {
		public void run() {
			sleepSec(4);
			synchronized(m) {
//				sync(s6);
				sync(s7);
				sleepSec(9);
				sync(s9);
				sync(s4);
			}
			sync(s13);
		}
	}
	
	public static class Test5 extends Thread implements Runnable {
		public void run() {
			sleepSec(8);
			synchronized(p) {
				sync(s8);
				sync(s9);
				sleepSec(10);
				sync(s7);
				sync(s3);
			}
			sync(s14);
		}
	}
	
	public static class Test6 extends Thread implements Runnable {
		public void run() {
			sleepSec(21);
			synchronized(o) {
				sync(s10);
				sync(s8);
			}
		}
	}
	
	public static class Test7 extends Thread implements Runnable {
		public void run() {
			sleepSec(22);
			sync(s10);
			int t = x;
		}
	}

	public static void main(String args[]) throws Exception {
		final Figure9a t1 = new Figure9a();
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
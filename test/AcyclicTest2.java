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

public class AcyclicTest2 extends Thread {

	static int x;
	static final Object o = new Object();
	static final Object m = new Object();
	static final Object p = new Object();
	
	static class SyncObject {
		int var;
	}
	
	static final SyncObject s1 = new SyncObject();
	static final SyncObject s2 = new SyncObject();
	static final SyncObject s3 = new SyncObject();
	static final SyncObject s4 = new SyncObject();
	static final SyncObject s5 = new SyncObject();
	static final SyncObject s6 = new SyncObject();
	static final SyncObject s7 = new SyncObject();
	static final SyncObject s8 = new SyncObject();
	static final SyncObject s9 = new SyncObject();
	static final SyncObject s10 = new SyncObject();
	static final SyncObject s11 = new SyncObject();
	static final SyncObject s12 = new SyncObject();

	// Predictable race: ?
	// WDC-race: yes
	// WCP-race: no
	// CP-race: no

	static void sync(SyncObject lock) {
		synchronized (lock) {
			lock.var = 1;
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
			sync(s12);
			x = 1;
			sync(s1);
		}
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			synchronized(m) {
				sync(s11);
				sync(s2);
				sync(s1);
			}
			//sync(s7);
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			sleepSec(2);
			synchronized(p) {
				sync(s3);
				sync(s2);
			}
			//sync(s8);
		}
	}
	
	public static class Test4 extends Thread implements Runnable {
		public void run() {
			sleepSec(3);
			synchronized(m) {
				sync(s4);
				sync(s3);
			}
			sync(s9);
		}
	}
	
	public static class Test5 extends Thread implements Runnable {
		public void run() {
			sleepSec(4);
			synchronized(p) {
				sync(s5);
				sync(s4);
				sync(s11);
				sync(s12);
			}
			//sync(s10);
		}
	}
	
	public static class Test6 extends Thread implements Runnable {
		public void run() {
			sleepSec(5);
			synchronized(o) {
				sync(s6);
				sync(s5);
			}
		}
	}
	
	public static class Test7 extends Thread implements Runnable {
		public void run() {
			sleepSec(6);
			sync(s7);
			sync(s8);
			sync(s9);
			sync(s10);
			sync(s6);
			int t = x;
		}
	}

	public static void main(String args[]) throws Exception {
		final AcyclicTest2 t1 = new AcyclicTest2();
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
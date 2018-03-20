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

public class OE2 extends Thread {

	static int x;
	static int qV;
	static final Object p = new Object();
	static final Object m = new Object();
	static final Object t = new Object();
	static final Object o = new Object();
	static final Object q = new Object();
	static final Object r = new Object();
	
	//WDC: race on x
	//WCP: race on x
	//CPMode: race on variable x
	//HBMode: no race
	
	@Override
	public void run() {
		int v = x; //x = 1;
//		synchronized(m) {}
		synchronized(o) {}
		synchronized(t) {}
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			try{Thread.sleep(500);}catch(Exception e){}
			synchronized(t) {}
			synchronized(m) {}
			synchronized(q) {
				qV = 1;
			}
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			try{Thread.sleep(600);}catch(Exception e){}
			synchronized(o) {
				synchronized(p) {}
				try{Thread.sleep(2000);}catch(Exception e){}
				synchronized(q) {
					qV = 1;
				}
			}
		}
	}

	public static class Test4 extends Thread implements Runnable {
		public void run() {
			try{Thread.sleep(1000);}catch(Exception e){}
			synchronized(m) {
				synchronized(r) {}
				synchronized(p) {}
			}
		}
	}

	public static class Test5 extends Thread implements Runnable {
		public void run() {
			try{Thread.sleep(1200);}catch(Exception e){}
			synchronized(r) {}
			x = 1;
		}
	}

	public static void main(String args[]) throws Exception {
		final OE2 t1 = new OE2();
		final Test2 t2 = new Test2();
		final Test3 t3 = new Test3();
		final Test4 t4 = new Test4();
		final Test5 t5 = new Test5();
		try{Thread.sleep(500);}catch(Exception e){}
		t1.start();
		t2.start();
		t3.start();
		t4.start();
		t5.start();
		t1.join();
		t2.join();
		t3.join();
		t4.join();
		t5.join();
	}
}

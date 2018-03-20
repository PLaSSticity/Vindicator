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

public class example86 extends Thread {

	static int x;
	static int L16Var;
	static int L4Var;
	static final Object L16 = new Object();
	static final Object L4 = new Object();
	static final Object L7 = new Object();
		
	@Override
	public void run() {
		synchronized(L7) {
			x = 1;
		}
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			try{Thread.sleep(1000);}catch(Exception e){}
			synchronized(L7) {
				synchronized(L16) {
					L16Var = 1;
				}
				try{Thread.sleep(3000);}catch(Exception e){}
				synchronized(L4) {
					L4Var = 1;
				}
			}
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			try{Thread.sleep(2000);}catch(Exception e){}
			synchronized(L16) {
				L16Var = 1;
			}
			x = 1;
			synchronized(L4) {
				L4Var = 1;
			}
		}
	}

	public static void main(String args[]) throws Exception {
		final example86 t1 = new example86();
		final Test2 t2 = new Test2();
		final Test3 t3 = new Test3();
		try{Thread.sleep(500);}catch(Exception e){}
		t1.start();
		t2.start();
		t3.start();
		t1.join();
		t2.join();
		t3.join();
	}
}

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

public class AcyclicTest3 extends Thread {

	static final boolean PRINT = true;
	
	static abstract class SpecialObject {
		String varName;
		
		SpecialObject(String varName) {
			this.varName = varName;
		}
		
		void printEvent(String s) {
			if (PRINT) {
				synchronized (SpecialObject.class) {
					String thrName = Thread.currentThread().getName();
					int thrNum = Integer.parseInt(thrName.substring(thrName.length() - 1));
					System.out.println(indent(thrNum) + s);
				}
			}
		}
		
		String indent(int num) {
			if (num == 0) {
				return "";
			}
			return "            " + indent(num - 1);
		}
	}
	
	static class VarObject extends SpecialObject {
		int var;
		
		VarObject(String varName) {
			super(varName);
		}
		
		void write(int value) {
			var = value;
			printEvent("wr(" + varName + ")");
		}
	}
	
	static class LockObject extends SpecialObject {
		LockObject(String varName) {
			super(varName);
		}
		
		void acq() {
			printEvent("acq(" + varName + ")");
		}
		
		void rel() {
			printEvent("rel(" + varName + ")");
		}
	}
	
	static class SyncObject extends SpecialObject {
		boolean var;
		
		SyncObject(String varName) {
			super(varName);
		}
		
		void source() {
			printEvent("sync(" + varName + ")");
			synchronized (this) {
				var = true;
			}
		}
		
		void sink() {
			sink(0);
		}
		
		void sink(int extraSec) {
			try {
				Thread.sleep((1 + extraSec) * 1000);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			synchronized (this) {
				if (!var) {
					throw new RuntimeException("Oops");
				}
			}
			printEvent("sync(" + varName + ")");
		}
		
		void check() {
			if (!var) {
				throw new RuntimeException("Oops");
			}
		}
	}

	static final VarObject x = new VarObject("x");
	
	static final LockObject o = new LockObject("o");
	static final LockObject m = new LockObject("m");
	static final LockObject p = new LockObject("p");
	
	static final SyncObject s1 = new SyncObject("s1");
	static final SyncObject s2 = new SyncObject("s2");
	static final SyncObject s3 = new SyncObject("s3");
	static final SyncObject s4 = new SyncObject("s4");
	static final SyncObject s5 = new SyncObject("s5");
	static final SyncObject s6 = new SyncObject("s6");
	static final SyncObject s7 = new SyncObject("s7");
	static final SyncObject s8 = new SyncObject("s8");
	static final SyncObject s9 = new SyncObject("s9");
	static final SyncObject s10 = new SyncObject("s10");
	static final SyncObject s11 = new SyncObject("s11");
	static final SyncObject s12 = new SyncObject("s12");
	static final SyncObject s13 = new SyncObject("s13");
	static final SyncObject s14 = new SyncObject("s14");

	static void sleepSec(float sec) {
		try{
			Thread.sleep((long)(sec * 1000));
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

/*
SMT constraints for this example:

(declare-const T2_acq_m Int)
(declare-const T2_rel_m Int)
(declare-const T3_acq_p Int)
(declare-const T3_rel_p Int)
(declare-const T4_acq_m Int)
(declare-const T4_rel_m Int)
(declare-const T5_acq_p Int)
(declare-const T5_rel_p Int)

(declare-const T1_acq_o Int)
(declare-const T1_wr_x Int)
(declare-const T1_rel_o Int)
(declare-const T6_acq_o Int)
(declare-const T6_rel_o Int)
(declare-const T7_wr_x Int)

; PO
(assert (< T2_acq_m T2_rel_m))
(assert (< T3_acq_p T3_rel_p))
(assert (< T4_acq_m T4_rel_m))
(assert (< T5_acq_p T5_rel_p))

(assert (< T1_acq_o T1_wr_x T1_rel_o))
(assert (< T6_acq_o T6_rel_o))

; LS 
(assert (or (< T2_rel_m T4_acq_m) (< T4_rel_m T2_acq_m)))
(assert (or (< T3_rel_p T5_acq_p) (< T5_rel_p T3_acq_p)))

(assert (or (< T1_rel_o T6_acq_o) (< T6_rel_o T1_acq_o)))

; Syncs
(assert (< T2_acq_m T3_rel_p))
(assert (< T3_acq_p T4_rel_m))
(assert (< T4_acq_m T5_rel_p))

(assert (< T2_acq_m T5_rel_p))
(assert (< T4_acq_m T3_rel_p))
(assert (< T3_acq_p T2_rel_m))
(assert (< T5_acq_p T4_rel_m))

(assert (< T1_acq_o T2_rel_m))
(assert (< T2_rel_m T1_wr_x))
(assert (< T4_rel_m T1_wr_x))
(assert (< T3_rel_p T1_wr_x))
(assert (< T5_rel_p T1_wr_x))
(assert (< T5_acq_p T6_rel_o))
(assert (< T6_acq_o T7_wr_x))

; Race
(assert (= T1_wr_x T7_wr_x))

; Start at zero for ease of reading
(assert (= T1_acq_o 0))

(check-sat)
(get-model)

It's unsatisfiable. To see the original execution, exclude the "Race" constraint and get this:

T2_acq_m 0
T3_acq_p 0
T1_acq_o 0
T2_rel_m 1
T4_acq_m 2
T3_rel_p 3
T5_acq_p 4
T5_rel_p 5
T4_rel_m 5
T1_wr_x  6
T1_rel_o 7
T6_acq_o 8
T6_rel_o 9
T7_wr_x  9
*/

	@Override
	public void run() {
		synchronized(o) {
			o.acq();
			s1.source();
			s11.sink(2);
			s12.sink(1);
			s13.sink();
			s14.sink();
			x.write(1);
			o.rel();
		}
	}
	
	public static class Test2 extends Thread implements Runnable {
		public void run() {
			synchronized (m) {
				m.acq();
				s2.source();
				s3.source();
				s1.sink();
				s4.sink();
				m.rel();
			}
			s11.source();
		}
	}
	
	public static class Test3 extends Thread implements Runnable {
		public void run() {
			synchronized (p) {
				p.acq();
				s4.source();
				s7.source();
				s2.sink();
				s5.sink(1);
				p.rel();
			}
			s13.source();
		}
	}
	
	public static class Test4 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			synchronized (m) {
				m.acq();
				s5.source();
				s6.source();
				s7.sink();
				s8.sink();
				m.rel();
			}
			s12.source();
		}
	}
	
	public static class Test5 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			synchronized (p) {
				p.acq();
				s8.source();
				s9.source();
				s3.sink();
				s6.sink();
				p.rel();
			}
			s14.source();
		}
	}
	
	public static class Test6 extends Thread implements Runnable {
		public void run() {
			sleepSec(1);
			synchronized (o) {
				o.acq();
				s10.source();
				s9.sink();
				o.rel();
			}
		}
	}
	
	public static class Test7 extends Thread implements Runnable {
		public void run() {
			s10.sink(8);
			x.write(2);
		}
	}

	public static void main(String args[]) throws Exception {
		final AcyclicTest3 t1 = new AcyclicTest3();
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
		
		/*s1.check();
		s2.check();
		s3.check();
		s4.check();
		s5.check();
		s6.check();
		s7.check();
		s8.check();
		s9.check();
		s10.check();
		s11.check();
		s12.check();
		s13.check();
		s14.check();*/

		System.out.println(x.var);
	}
}

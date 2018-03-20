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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FuturesTest extends Thread {

	static int x;
	static int y = 0;
	static int z = 0;
	static int u = 0;
	static final Object m = new Object();
	static final Object n = new Object();
	
	static int oVar;
	static int pVar;
	static int qVar;
	static int rVar;
	static final Object o = new Object();
	static final Object p = new Object();
	static final Object q = new Object();
	static final Object r = new Object();

	// Predictable race: none
	// WDC-race: x
	// WCP-race: ?
	// CP-race: ?

	static void sync(Object lock) {
		synchronized (lock) {
			if (lock == o) oVar = 1;
			else if (lock == p) pVar = 1;
			else if (lock == q) qVar = 1;
			else if (lock == r) rVar = 1;
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
		synchronized(m) {
			sync(p);
			sync(q);
			x = 1;
		}
	}

	public static void main(String args[]) throws Exception {
		final FuturesTest t1 = new FuturesTest();
		t1.start();
		t1.join();
		
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Integer>> tasks = new LinkedList<Future<Integer>>();

        for (int id = 0; id < 4; id++) {
        	int t = y;
        	Callable pmdCall = new Callable<Integer>() {
            	@Override
            	public Integer call() {
            		synchronized(r) {}
            		x = 1;
            		sync(r);
            		return 0;
            	}
            };
        	int a = u;
            Future<Integer> future = executor.submit(pmdCall);
            int f = z;
            tasks.add(future);
        }
        executor.shutdown();

        while (!tasks.isEmpty()) {
            Future<Integer> future = tasks.remove(0);
            Integer report = 0;
            try {
                report = future.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                future.cancel(true);
            } catch (ExecutionException ee) {
                Throwable t = ee.getCause();
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else if (t instanceof Error) {
                    throw (Error) t;
                } else {
                    throw new IllegalStateException("FuturesTestRunnable exception", t);
                }
            }
        }
	}
}
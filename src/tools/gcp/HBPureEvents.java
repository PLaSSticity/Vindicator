package tools.gcp;

import java.io.BufferedWriter;
import java.lang.ref.WeakReference;

import rr.event.AccessEvent;
import rr.event.Event;
import rr.event.JoinEvent;
import rr.event.StartEvent;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.tool.RR;
import acme.util.Util;

public class HBPureEvents {

	static BufferedWriter out;
	
	static final boolean printEvent = RR.printEventOption.get();
	 
	static void acquire(ShadowThread thr, Lock lock, ShadowLock sl) {
		if (printEvent) Util.message("T" + thr.getTid() + ": HBacq " + lock.name);
		if (GCPTool.PrintStats) GCPStats.lastAcquireEvent += LocksetVar.locksetVars.size();
		long start_time = System.nanoTime();
		for (LocksetVar varLS : LocksetVar.locksetVars) {
			addHBElements(varLS, lock, thr, Type.HB);
			varLS.printLockSet(GCPEvents.out);
		}
		if (GCPTool.PrintStats) GCPStats.lastTimeAcquireEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeAcquireEvent += GCPStats.lastTimeAcquireEvent;
	}
	
	static void release(ShadowThread thr, Lock lock) {
		if (printEvent) Util.message("T" + thr.getTid() + ": HBrel " + lock.name);
		if (GCPTool.PrintStats) GCPStats.lastReleaseEvent += GCPTool.gcpThread.get(thr).VarLocksetsContainingThr.size();
		long start_time = System.nanoTime();
		for (LocksetVar varLS : GCPTool.gcpThread.get(thr).VarLocksetsContainingThr) {
			addHBElements(varLS, thr, lock, Type.HB);
			varLS.printLockSet(GCPEvents.out);
		}
		if (GCPTool.PrintStats) GCPStats.lastTimeReleaseEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeReleaseEvent += GCPStats.lastTimeReleaseEvent;
	}
	
	private static boolean addHBElements(LocksetVar var, Lock checkLock, ShadowThread addThread, Type type) {
		if (var.lockset.containsExactly(checkLock, type)) {
			var.lockset.add(addThread, type);
			GCPTool.gcpThread.get(addThread).VarLocksetsContainingThr.add(var);
			return true;
		}
		return false;
	}
	
	private static boolean addHBElements(LocksetVar var, ShadowThread checkThread, Lock addLock, Type type) {
		if (var.lockset.containsExactly(checkThread, type)) {
			var.lockset.addHBLock(addLock, type, 0, true);
			return true;
		}
		return false;
	}
	
	static WriteVar wri(ShadowThread T, Var x_0, Event event, long eventOrder) {
		if (printEvent) Util.message("T" + T.getTid() + ": HBwr " + x_0 + " ver: " + x_0.version);
		long start_time = System.nanoTime();
		WriteVar x_iMinus1 = (WriteVar) x_0;
		if (x_0.latestInstance != null) {
			x_iMinus1 = (WriteVar) x_0.latestInstance;
		}
		
		//Check for write-write race
		if (!isPOOrdered(x_iMinus1, T) && !x_iMinus1.lockset.containsExactly(T, Type.HB)) {
			x_iMinus1.setNextWriteInstance(event, eventOrder);
			GCPTool.error(x_iMinus1, null, "HB");
		}
		//Check for read-write race
		for (ShadowThread t : ShadowThread.getThreads()) {
			ReadVar x_iMinus1_t = (ReadVar) x_iMinus1.sameIndexReadInstance.get(t);
			if (x_iMinus1_t != null && !isPOOrdered(x_iMinus1_t, T) && !x_iMinus1_t.lockset.containsExactly(T, Type.HB)) {
				x_iMinus1_t.setNextWriteInstance(event, eventOrder);
				GCPTool.error(x_iMinus1_t, null, "HB");
			}
		}
		
		//Initialize x_i
		x_iMinus1.lockset.clear();
		x_iMinus1.sameIndexReadInstance.clear();
		x_iMinus1.nextRead_Event.clear();
		x_iMinus1.nextRead_Dist.clear();
		x_iMinus1.lockset.add(T, Type.HB);
		x_iMinus1.totalEventOrder = eventOrder;
		x_iMinus1.lastAccessedByThread = T;
		
		GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(x_iMinus1);
		x_0.latestInstance = x_iMinus1;
		
		x_iMinus1.printLockSet(out);
		if (GCPTool.PrintStats) GCPStats.lastTimeWriteEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeWriteEvent += GCPStats.lastTimeWriteEvent;
		return x_iMinus1;
	}
	
	static void rd(ShadowThread T, Var x_0, AccessEvent event, long eventOrder) {
		if (printEvent) Util.message("T" + T.getTid() + ": HBrd " + x_0 + " ver: " + x_0.version);
		long start_time = System.nanoTime();
		WriteVar x_i = (WriteVar) x_0;
		if (x_0.latestInstance != null)
			x_i = (WriteVar) x_0.latestInstance;
		
		//Check for write-read race
		if (!isPOOrdered(x_i, T) && !x_i.lockset.containsExactly(T, Type.HB)) {
			x_i.nextRead_Event.put(T, event);
			x_i.nextRead_Dist.put(T, eventOrder);
			GCPTool.error(x_i, T, "HB");
		}
		
		//Initialize x_i_T
		ReadVar x_i_T = x_i.sameIndexReadInstance.get(T);
		if (x_i_T == null) {
			x_i_T = new ReadVar(event, T, x_i.version, false);
		} else {
			x_i_T.lockset.clear();
		}
		x_i_T.lockset.add(T, Type.HB);
		x_i_T.totalEventOrder = eventOrder;
		x_i_T.lastAccessedByThread = T;
		
		GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(x_i_T);
		x_i.sameIndexReadInstance.put(T, x_i_T);
		
		if (GCPTool.PrintStats) GCPStats.lastTimeReadEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeReadEvent += GCPStats.lastTimeReadEvent;
	}
	
	private static boolean isPOOrdered(Var var, ShadowThread thr) {
		return var.lastAccessedByThread == Var.allThr || (!var.lockset.isEmpty() && var.lastAccessedByThread.equals(thr));
	}
}
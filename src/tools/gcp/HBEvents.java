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

public class HBEvents {
	
	static BufferedWriter out;
	
	static final boolean printEvent = RR.printEventOption.get();
	 
	static void acquire(ShadowThread thr, Lock l, ShadowLock sl) {
		if (printEvent) Util.message("T" + thr.getTid() + ": HBacq " + l.name);
		long start_time = System.nanoTime();
		Lock lock = l;
		if (l.latestLock != null)
			lock = l.latestLock;
		if (GCPTool.PrintStats) GCPStats.lastAcquireEvent += LocksetVar.locksetVars.size();
		for (LocksetVar varLS : LocksetVar.locksetVars) {
			addHBElements(varLS, lock, thr, Type.HB);
			varLS.printLockSet(GCPEvents.out);
		}
		Lock newestLock = l.acq(thr, sl, l.getLockID());
		newestLock.lockset.add(thr, Type.HB);
		newestLock.printLockSet(GCPEvents.out);
		l.latestLock = newestLock;
		if (GCPTool.PrintStats) GCPStats.lastTimeAcquireEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeAcquireEvent += GCPStats.lastTimeAcquireEvent;
	}
	
	static void release(ShadowThread thr, Lock l) {
		if (printEvent) Util.message("T" + thr.getTid() + ": HBrel " + l.name);
		long start_time = System.nanoTime();
		Lock lock = l.latestLock;
		if (GCPTool.PrintStats) GCPStats.lastReleaseEvent += GCPTool.gcpThread.get(thr).VarLocksetsContainingThr.size();
		for (LocksetVar varLS : GCPTool.gcpThread.get(thr).VarLocksetsContainingThr) {
			addHBElements(varLS, thr, lock, Type.HB);
			varLS.printLockSet(GCPEvents.out);
		}
		lock.rel();
		LocksetLock.locksetLocks.remove(lock);
		if (GCPTool.PrintStats) GCPStats.lockLocksetRemoved++;
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
		
		//Initialize x_i 
		WriteVar x_i = initializeWriteLockset(x_iMinus1, T, event, eventOrder);
		x_0.latestInstance = x_i;
		
		//Check for read-write race
		for (ShadowThread t : ShadowThread.getThreads()) {
			ReadVar x_iMinus1_t = (ReadVar) x_iMinus1.sameIndexReadInstance.get(t);
			if (x_iMinus1_t != null) {
				if(!isPOOrdered(x_iMinus1_t, T) && !x_iMinus1_t.lockset.containsExactly(T, Type.HB)) {
					GCPTool.error(x_iMinus1_t, null, "HB");
				}
				LocksetVar.locksetVars.remove(x_iMinus1_t);
				if (GCPTool.PrintStats) GCPStats.readLocksetRemoved++;
			}
		}
		//Check for write-write race
		if (!isPOOrdered(x_iMinus1, T) && !x_iMinus1.lockset.containsExactly(T, Type.HB)) {
			GCPTool.error(x_iMinus1, null, "HB");
		}
		LocksetVar.locksetVars.remove(x_iMinus1);
		if (GCPTool.PrintStats) GCPStats.writeLocksetRemoved++;
		
		if (GCPTool.PrintStats) GCPStats.lastTimeWriteEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeWriteEvent += GCPStats.lastTimeWriteEvent;
		return x_i;
	}
	
	static void rd(ShadowThread T, Var x_0, AccessEvent event, long eventOrder) {
		if (printEvent) Util.message("T" + T.getTid() + ": HBrd " + x_0 + " ver: " + x_0.version);
		long start_time = System.nanoTime();
		WriteVar x_i = (WriteVar) x_0;
		if (x_0.latestInstance != null)
			x_i = (WriteVar) x_0.latestInstance;
		
		//Initialize x_i_T
		ReadVar x_i_T = initializeReadLockset(x_i, T, event, eventOrder);
		x_i.sameIndexReadInstance.put(T, x_i_T);
		
		//Check for write-read race
		if (!isPOOrdered(x_i, T) && !x_i.lockset.containsExactly(T, Type.HB)) {
			GCPTool.error(x_i, T, "HB");
		}
		
		if (GCPTool.PrintStats) GCPStats.lastTimeReadEvent = System.nanoTime() - start_time;
		if (GCPTool.PrintStats) GCPStats.timeReadEvent += GCPStats.lastTimeReadEvent;
	}
	
	private static boolean isPOOrdered(Var var, ShadowThread thr) {
		return var.lastAccessedByThread == Var.allThr || (!var.lockset.isEmpty() && var.lastAccessedByThread.equals(thr));
	}
	
	private static WriteVar initializeWriteLockset(WriteVar x_iMinus1, ShadowThread T, Event event, long eventOrder) {
		WriteVar x_i = x_iMinus1.wri(T, x_iMinus1.version+1, event, eventOrder);
		x_i.lockset.add(T, Type.HB);
		x_i.totalEventOrder = eventOrder;
		GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(x_i);
		x_i.printLockSet(GCPEvents.out);
		return x_i;
	}
	
	private static ReadVar initializeReadLockset(WriteVar x_i, ShadowThread T, Event event, long eventOrder) {
		ReadVar x_i_T = x_i.sameIndexReadInstance.get(T);
		if (x_i_T == null) {
			x_i_T = new ReadVar(event, T, x_i.version, false);
			x_i_T.setNextWriteInstance(x_i.getEvent(), eventOrder);
		} else {
			if (GCPTool.Verbose == 1) assert x_i_T.version == x_i.version;
			x_i_T.lockset.clear();
		}
		x_i_T.lockset.add(T, Type.HB);
		x_i_T.totalEventOrder = eventOrder;
		x_i_T.lastAccessedByThread = T;
		GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(x_i_T);
		x_i_T.printLockSet(GCPEvents.out);
		return x_i_T;
	}
}
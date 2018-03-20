package tools.gcp;

import java.lang.ref.WeakReference;

import acme.util.Util;

import rr.event.AccessEvent;
import rr.event.Event;
import rr.event.JoinEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.state.ShadowLock;
import rr.state.ShadowThread;

public class GCPWrite {
	static WriteVar wri(ShadowThread T, Var x_0, Event event, long eventOrder) {
			WriteVar x_iMinus1 = (WriteVar) x_0;
			if (x_0.latestInstance != null)
				x_iMinus1 = (WriteVar) x_0.latestInstance;
			
			//Apply Rule A. For volatile, start, and join events, additional dummy locks are checked.
			if (event instanceof VolatileAccessEvent) {
				applyRuleA(x_0.getVolatileLock().shadowLock, (WriteVar) x_0, x_iMinus1, T);
			}
			if (ShadowThread.getCurrentShadowThread().equals(T)) { //do not apply for the child thread of StartEvent and JoinEvent
				for (ShadowLock m : T.getLocksHeld()) {
					applyRuleA(m, (WriteVar) x_0, x_iMinus1, T);
				}
			}
			
			//Add xi to x_iMinus1 to represent T at wr(x_i) [Write-Write]
			if (isPOOrdered(x_iMinus1, T) || x_iMinus1.lockset.containsExactly(T, Type.CP)) {
				x_iMinus1.lockset.setUniqueWriteThr(T, Type.CP, 0);
			} else {
				int count = x_iMinus1.lockset.uniqueWriteThrModifier==null?0:x_iMinus1.lockset.uniqueWriteThrModifier.uniqueCount;
				if (x_iMinus1.lockset.containsExactly(T, Type.HB)){
					x_iMinus1.lockset.setUniqueWriteThr(T, Type.HB, (count==-1?0:count) + addUniqueCCPElements(x_iMinus1, T));
				} else {
					x_iMinus1.lockset.setUniqueWriteThr(T, Type.PCP, (count==-1?0:count) + addUniqueCCPElements(x_iMinus1, T));
				}
				x_iMinus1.printLockSet(GCPEvents.out);
			}
			
			//Add xi to x_iMinus1_t to represent T at wr(x_i) [Read-Write] 
			for (ShadowThread t : ShadowThread.getThreads()) {
				ReadVar x_iMinus1_t = x_iMinus1.sameIndexReadInstance.get(t);
				if (x_iMinus1_t != null) {
					if (isPOOrdered(x_iMinus1_t, T) || x_iMinus1_t.lockset.containsExactly(T, Type.CP)) {
						x_iMinus1_t.lockset.setUniqueWriteThr(T, Type.CP, 0);
					} else {
						int count = x_iMinus1_t.lockset.uniqueWriteThrModifier==null?0:x_iMinus1_t.lockset.uniqueWriteThrModifier.uniqueCount;
						if (x_iMinus1_t.lockset.containsExactly(T, Type.HB)) {
							x_iMinus1_t.lockset.setUniqueWriteThr(T, Type.HB, (count==-1?0:count) + addUniqueCCPElements(x_iMinus1_t, T));
						} else {
							x_iMinus1_t.lockset.setUniqueWriteThr(T, Type.PCP, (count==-1?0:count) + addUniqueCCPElements(x_iMinus1_t, T));
						}
						x_iMinus1_t.printLockSet(GCPEvents.out);
					}
				}
			}
			
			//Initialize locksets for x_i
			WriteVar x_i = initializeLockset(x_iMinus1, T, event, eventOrder);
			x_0.latestInstance = x_i;
			
			//Quick check for write-write races
			if (x_iMinus1.lockset.uniqueWriteThr != null && x_iMinus1.lockset.uniqueWriteThrModifier.uniqueCount == 0 && x_iMinus1.lockset.uniqueWriteThrModifier.type != Type.CP) {
				GCPEvents.reportWrRace(x_iMinus1);
			}
			//Quick check for read-write races
			for (ShadowThread t : ShadowThread.getThreads()) {
				ReadVar x_iMinus1_t = x_iMinus1.sameIndexReadInstance.get(t);
				if (x_iMinus1_t != null && x_iMinus1_t.lockset.uniqueWriteThr!= null && x_iMinus1_t.lockset.uniqueWriteThrModifier.uniqueCount == 0 && x_iMinus1_t.lockset.uniqueWriteThrModifier.type != Type.CP) {
					GCPEvents.reportWrRace(x_iMinus1_t);
				}
			}
			
			return x_i;
	}
	
	private static void applyRuleA(ShadowLock m, WriteVar x_0, WriteVar x_iMinus1, ShadowThread T) {
		WriteVar x_h = x_iMinus1;
		while (x_h != null) {
			for (ShadowThread t : x_h.sameIndexReadInstance.keySet()) {
				if (GCPTool.PrintStats) GCPStats.lastWriteEvent++;
				ReadVar x_h_t = x_h.sameIndexReadInstance.get(t);
				if (x_h_t != null && !isPOOrdered(x_h_t, T)) {
					for (ShadowLock shadowLock : x_h_t.lockset.locks.keySet()) {
						Lock m_j = x_h_t.lockset.locks.get(shadowLock).getLockInstance();
						if (m_j.shadowLock.equals(m) && x_h_t.lockset.containsExactly(m_j, Type.LS)) {
							updateLockset(m_j, T);
							x_h = null;
							break;
						}
					}
				}
				if (x_h == null) {
					break;
				}
			}
			if (GCPTool.PrintStats) GCPStats.lastWriteEvent++;
			if (x_h != null && !isPOOrdered(x_h, T)) {
				for (ShadowLock shadowLock : x_h.lockset.locks.keySet()) {
					Lock m_j = x_h.lockset.locks.get(shadowLock).getLockInstance();
					//TODO: If Type.LS is a separate lockset, then only those elements would be looked at here.
					if (m_j.shadowLock.equals(m) && x_h.lockset.containsExactly(m_j, Type.LS)) {
						updateLockset(m_j, T);
						x_h = null;
						break;
					}
				}
			}
			if (x_h != null) {
				x_h = x_h.priorWriteInstance == null ? null : x_h.priorWriteInstance.get();
			}
		}
	}

	private static void updateLockset(Lock m_j, ShadowThread T) {
		m_j.lockset.add(T, Type.CP);
		GCPTool.gcpThread.get(T).LockLocksetsContainingThr.add(m_j);
		m_j.printLockSet(GCPEvents.out);
	}
	
	private static boolean isPOOrdered(Var var, ShadowThread T) {
		return var.lastAccessedByThread == Var.allThr || (!var.lockset.isEmpty() && var.lastAccessedByThread.equals(T));
	}
	
	private static int addUniqueCCPElements (Var var, ShadowThread T) {
		int elementsAdded = 0;
		for (Lock m : var.lockset.PCPlocks.keySet()) {
			if (var.lockset.PCPlocks.get(m).PCPthreads.contains(T)) {
				var.lockset.add(T, m, true, true);
				GCPTool.gcpLock.get(m.shadowLock).VarResolvingConditionalCP.add(var);
				elementsAdded++;
			}
		}
		return elementsAdded;
	}
	
	private static WriteVar initializeLockset(WriteVar x_iMinus1, ShadowThread T, Event event, long eventOrder) {
		WriteVar x_i = x_iMinus1.wri(T, x_iMinus1.version+1, event, eventOrder);
		x_i.lockset.add(T, Type.HB);
		if (ShadowThread.getCurrentShadowThread().equals(T)) { //do not apply for the child thread of StartEvent and JoinEvent
			for (ShadowLock m : T.getLocksHeld()) {
				x_i.lockset.addHBLock(GCPTool.get(m).latestLock, Type.LS, GCPTool.get(m).latestLock.latestVersion, false);
			}
		}
		if (event instanceof VolatileAccessEvent) {
			x_i.lockset.addHBLock(x_iMinus1.getVolatileLock().latestLock, Type.LS, x_iMinus1.getVolatileLock().latestVersion, false);
		}
		x_i.totalEventOrder = eventOrder;
		GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(x_i);
		x_i.printLockSet(GCPEvents.out);
		return x_i;
	}
}

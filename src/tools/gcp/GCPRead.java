package tools.gcp;

import acme.util.Util;
import rr.event.AccessEvent;
import rr.event.Event;
import rr.event.VolatileAccessEvent;
import rr.state.ShadowLock;
import rr.state.ShadowThread;

public class GCPRead {
	static void rd(ShadowThread T, Var x_0, Event event, long eventOrder) {
		WriteVar x_i = (WriteVar) x_0;
		if (x_0.latestInstance != null) {
			x_i = (WriteVar) x_0.latestInstance;
		}
		
		//Apply Rule A. For volatile, additional dummy locks are checked. Start and Join events don't generate reads, so they are not checked.
		if (event instanceof VolatileAccessEvent) {
			applyRuleA(x_0.getVolatileLock().shadowLock, (WriteVar) x_0, x_i, T);
		}
		for (ShadowLock m : T.getLocksHeld()) {
			applyRuleA(m, (WriteVar) x_0, x_i, T);
		}
		
		//Add xi to x_i to represent T at rd(x_i_t) [Write-Read]
		if (isPOOrdered(x_i, T) || x_i.lockset.containsExactly(T, Type.CP)) {
			x_i.lockset.setUniqueReadThr(T, Type.CP, 0);
		} else {
			int count = 0;
			if (x_i.lockset.uniqueReadThr != null) {
				count = x_i.lockset.uniqueReadThr.get(T)==null?0:x_i.lockset.uniqueReadThr.get(T).uniqueCount;
			}
			
			if (x_i.lockset.containsExactly(T, Type.HB)){
				x_i.lockset.setUniqueReadThr(T, Type.HB, (count==-1?0:count) + addUniqueCCPElements(x_i, T));
			} else {
				x_i.lockset.setUniqueReadThr(T, Type.PCP, (count==-1?0:count) + addUniqueCCPElements(x_i, T));
			}
		}
		
		//Initialize locksets for x_i_t
		ReadVar x_i_T = initializeLockset(x_i, T, event, eventOrder);
		x_i.sameIndexReadInstance.put(T, x_i_T);
		x_i.setNextReadInstance(event, eventOrder, T);
		
		//Quick check for write-read races
		if (!x_i.lockset.uniqueReadThr.isEmpty() && x_i.lockset.uniqueReadThr.containsKey(T) && x_i.lockset.uniqueReadThr.get(T).uniqueCount == 0 && x_i.lockset.uniqueReadThr.get(T).type != Type.CP) {
			GCPEvents.reportRdRace(x_i, T);
		}
	}
	
	private static void applyRuleA(ShadowLock m, WriteVar x_0, WriteVar x_i, ShadowThread T) {
		WriteVar x_h = x_i;
		while (x_h != null) {
			if (GCPTool.PrintStats) GCPStats.lastReadEvent++;
			if (isPOOrdered(x_h, T)) {
				for (ShadowLock shadowLock : x_h.lockset.locks.keySet()) {
					//TODO: If Type.LS is a separate lockset, then only those elements would be looked at here.
					Lock m_j = x_h.lockset.locks.get(shadowLock).getLockInstance();
					if (m_j.shadowLock.equals(m) && x_h.lockset.containsExactly(m_j, Type.LS)) {
						updateLockset(m_j, T);
						x_h = null;
						break;
					}
				}
				if (x_h != null) {
					x_h = x_h.priorWriteInstance == null ? null : x_h.priorWriteInstance.get();
				}
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
	
	private static ReadVar initializeLockset(WriteVar x_i, ShadowThread T, Event event, long eventOrder) {
			ReadVar x_i_T = x_i.sameIndexReadInstance.get(T);
			if (x_i_T == null) {
				x_i_T = new ReadVar(event, T, x_i.version, false);
				x_i_T.setNextWriteInstance(x_i.getEvent(), eventOrder);
			} else {
				if (GCPTool.Verbose == 1) assert x_i_T.version == x_i.version;
				x_i_T.lockset.clear();
			}
			x_i_T.lockset.add(T, Type.HB);
			for (ShadowLock m : T.getLocksHeld()){
				x_i_T.lockset.addHBLock(GCPTool.get(m).latestLock, Type.LS, GCPTool.get(m).latestVersion, false);
			}
			if (event instanceof VolatileAccessEvent) {
				x_i_T.lockset.addHBLock(x_i.getVolatileLock().latestLock, Type.LS, x_i.getVolatileLock().latestVersion, false);
			}
			x_i_T.totalEventOrder = eventOrder;
			x_i_T.lastAccessedByThread = T;
			GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(x_i_T);
			x_i_T.printLockSet(GCPEvents.out);
			return x_i_T;
	}
	
	private static int addUniqueCCPElements (Var var, ShadowThread T) {
		int elementsAdded = 0;
		for (Lock m : var.lockset.PCPlocks.keySet()) {
			if (var.lockset.PCPlocks.get(m).PCPthreads.contains(T)) {
				var.lockset.add(T, m, true, false);
				GCPTool.gcpLock.get(m.shadowLock).VarResolvingConditionalCP.add(var);
				elementsAdded++;
			}
		}
		return elementsAdded;
	}
}

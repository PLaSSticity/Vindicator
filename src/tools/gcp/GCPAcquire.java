package tools.gcp;

import java.util.HashSet;
import java.util.Set;

import acme.util.Util;
import rr.state.ShadowLock;
import rr.state.ShadowThread;

public class GCPAcquire {
	static void acquire(ShadowThread T, Lock m_0, ShadowLock sl) {
		//For all rho associated with variable locksets: Apply ordering from m to T
		if (GCPTool.PrintStats) GCPStats.lastAcquireEvent += LocksetVar.locksetVars.size();
		if (GCPTool.PrintStats) GCPStats.latestAcquireEvent = LocksetVar.locksetVars.size();
		for (LocksetVar rho_var : LocksetVar.locksetVars) {
			if (!addHB_CPThread(rho_var, m_0, T, Type.CP)) {
				addCCPThread(rho_var, m_0, T);
				if (addHB_CPThread(rho_var, m_0, T, Type.HB)) {
					rho_var.lockset.add(T, rho_var.lockset.locks.get(m_0.shadowLock).getLockInstance(), false, false);
				}
			}
			rho_var.printLockSet(GCPEvents.out);
		}
		
		//For all rho associated with lock locksets: Apply ordering from m to T
		if (GCPTool.PrintStats) GCPStats.lastAcquireEvent += LocksetLock.locksetLocks.size();
		if (GCPTool.PrintStats) GCPStats.latestAcquireEvent = LocksetLock.locksetLocks.size();
		for (LocksetLock rho_lock : LocksetLock.locksetLocks) {
			if (!addHB_CPThread(rho_lock, m_0, T, Type.CP)) {
				addCCPThread(rho_lock, m_0, T);
				if (addHB_CPThread(rho_lock, m_0, T, Type.HB)) {
					rho_lock.lockset.add(T, rho_lock.lockset.locks.get(m_0.shadowLock).getLockInstance(), false, false);
				}
			}
			rho_lock.printLockSet(GCPEvents.out);
		}
		
		//Initialize locksets for m_i
		Lock m_i = m_0.acq(T, sl, m_0.getLockID());
		m_i.lockset.add(T, Type.HB);
		GCPTool.gcpThread.get(T).LockLocksetsContainingThr.add(m_i);
		m_i.printLockSet(GCPEvents.out);
		m_0.latestLock = m_i;
	}
	
	private static boolean addHB_CPThread(LocksetVar rho_var, Lock m_0, ShadowThread T, Type type) {
		if (rho_var.lockset.containsExactly(m_0, type) || (type == Type.HB && rho_var.lockset.containsExactly(m_0, Type.LS))) {
			rho_var.lockset.add(T, type);
			GCPTool.gcpThread.get(T).VarLocksetsContainingThr.add(rho_var);
			return true;
		}
		return false;
	}
	
	private static boolean addHB_CPThread(LocksetLock rho_lock, Lock m_0, ShadowThread T, Type type) {
		if (rho_lock.lockset.containsExactly(m_0, type) || (type == Type.HB && rho_lock.lockset.containsExactly(m_0, Type.LS))) {
			rho_lock.lockset.add(T, type);
			GCPTool.gcpThread.get(T).LockLocksetsContainingThr.add(rho_lock);
			return true;
		}
		return false;
	}
	
	private static void addCCPThread (LocksetVar rho_var, Lock m_0, ShadowThread T) {
		for (Lock n_k : rho_var.lockset.PCPlocks.keySet()) {
			for (Lock LHS_Lock : rho_var.lockset.PCPlocks.get(n_k).PCPlocks) {
				if (LHS_Lock.shadowLock.equals(m_0.shadowLock)) {
					rho_var.lockset.add(T, n_k, false, false);
				}
			}
		}
	}
	
	private static void addCCPThread (LocksetLock rho_lock, Lock m_0, ShadowThread T) {
		for (Lock n_k : rho_lock.lockset.PCPlocks.keySet()) {
			for (Lock LHS_Lock : rho_lock.lockset.PCPlocks.get(n_k).PCPlocks) {
				if (LHS_Lock.shadowLock.equals(m_0.shadowLock)) {
					rho_lock.lockset.add(T, n_k, false, false);
				}
			}
		}
	}
	
}

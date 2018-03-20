package tools.gcp;

import java.lang.ref.WeakReference;
import java.util.HashSet;

import rr.state.ShadowThread;
import rr.tool.RR;
import acme.util.Assert;
import acme.util.Util;

public class GCPRelease {
	static final boolean printLockset = RR.raptorPrintLocksetOption.get();
	static void release(ShadowThread T, Lock m_0) {
		Lock m_i = m_0;
		if (m_0.latestLock != null) {
			m_i = m_0.latestLock;
		}
		
		//For all rho associated with lock locksets: Apply ordering from T to m
		if (GCPTool.PrintStats) GCPStats.lastReleaseEvent += GCPTool.gcpThread.get(T).LockLocksetsContainingThr.size();
		for (LocksetLock rho_lock : GCPTool.gcpThread.get(T).LockLocksetsContainingThr) {
			if (!addCPLock(rho_lock, T, m_0)) {
				addCCPLock(rho_lock, T, m_0);
				addHBLock(rho_lock, T, m_i);
			}
			rho_lock.printLockSet(GCPEvents.out);
		}
			
		//For all rho associated with variable locksets: Apply ordering from T to m
		if (GCPTool.PrintStats) GCPStats.lastReleaseEvent += GCPTool.gcpThread.get(T).VarLocksetsContainingThr.size();
		for (LocksetVar rho_var : GCPTool.gcpThread.get(T).VarLocksetsContainingThr) {
			if (!addCPLock(rho_var, T, m_0)) {
				addCCPLock(rho_var, T, m_0);
				addHBLock(rho_var, T, m_i);
			}
			rho_var.printLockSet(GCPEvents.out);
		}
		
		//For all rho associated with variable locksets: Remove CCP elements conditional on m
//		HashSet<LocksetVar> rho_var_removal = new HashSet<LocksetVar>();
		for (LocksetVar rho_var_lockset : LocksetVar.locksetVars) {
			Var rho_var = (Var) rho_var_lockset;
			removeCCPElements(rho_var, m_0);
			
			//Check for read-write and write-write races
			if (rho_var.lockset.uniqueWriteThr != null && rho_var.lockset.uniqueWriteThrModifier.uniqueCount == 0 && rho_var.lockset.uniqueWriteThrModifier.type != Type.CP) {
				GCPEvents.reportWrRace(rho_var);
			}
			
			//Check for write-read races
			if (rho_var instanceof ReadVar && GCPTool.Verbose == 1) assert rho_var.lockset.uniqueReadThr.isEmpty();
			if (!rho_var.lockset.uniqueReadThr.isEmpty()) {
				for (ShadowThread xi_t : rho_var.lockset.uniqueReadThr.keySet()) {
					if (rho_var.lockset.uniqueReadThr.get(xi_t).uniqueCount == 0 && rho_var.lockset.uniqueReadThr.get(xi_t).type != Type.CP) {
						GCPEvents.reportRdRace(rho_var, xi_t);
					}
				}
			}
			
			//Remove variable locksets
//			if (rho_var instanceof ReadVar && rho_var.lockset.uniqueWriteThr != null && rho_var.lockset.uniqueWriteThrModifier.type == Type.CP) {
//				rho_var_removal.add(rho_var);
//			} else if (rho_var instanceof WriteVar && rho_var.lockset.uniqueWriteThr != null && rho_var.lockset.uniqueWriteThrModifier.type == Type.CP) {
//				if (rho_var.lockset.uniqueReadThr.isEmpty()) {
//					rho_var_removal.add(rho_var);
//				} else {
//					rho_var_removal.add(rho_var);
//					for (ShadowThread xi_t : rho_var.lockset.uniqueReadThr.keySet()) {
//						if (rho_var.lockset.uniqueReadThr.get(xi_t).type != Type.CP) {
//							rho_var_removal.remove(rho_var);
//							break;
//						}
//					}
//				}
//			}
			
			rho_var.printLockSet(GCPEvents.out);
		}
//		for (LocksetVar rho_var : rho_var_removal) {
//			LocksetVar.locksetVars.remove(rho_var);
//		}
			
		//For all rho associated with lock locksets: Remove CCP elements conditional on m
//		HashSet<LocksetLock> rho_lock_removal = new HashSet<LocksetLock>();
		for (LocksetLock rho_lock_lockset : LocksetLock.locksetLocks) {
			if (rho_lock_lockset == null)
				continue;
			Lock rho_lock = (Lock) rho_lock_lockset;
			removeCCPElements(rho_lock, m_0);
			
			//Remove lock locksets
//			if (rho_lock.removalCount == 0 && GCPTool.gcpLock.get(rho_lock.shadowLock).latestLock != null && !GCPTool.gcpLock.get(rho_lock.shadowLock).latestLock.equals(rho_lock)) {
//				rho_lock_removal.add(rho_lock);
//			}
			rho_lock.printLockSet(GCPEvents.out);
		}
//		for (LocksetLock rho_lock : rho_lock_removal) {
//			LocksetLock.locksetLocks.remove(rho_lock);
//		}
		
		m_0.VarResolvingConditionalCP.clear();
		m_i.VarResolvingConditionalCP.clear();
		m_i.rel();
	}
	
	private static boolean addCPLock(LocksetLock rho_lock, ShadowThread T, Lock m_0) {
		if (rho_lock.lockset.containsExactly(T, Type.CP)) {
			rho_lock.lockset.addCPLock(m_0, ((Lock)rho_lock).shadowLock.equals(m_0.shadowLock));
			return true;
		}
		return false;
	}
	
	private static boolean addCPLock(LocksetVar rho_var, ShadowThread T, Lock m_0) {
		if (rho_var.lockset.containsExactly(T, Type.CP)) {
			rho_var.lockset.addCPLock(m_0, false);
			return true;
		}
		return false;
	}
	
	private static void addCCPLock (LocksetLock rho_lock, ShadowThread T, Lock m_0) {
		for (Lock n_k : rho_lock.lockset.PCPlocks.keySet()) {
			if (!n_k.shadowLock.equals(m_0.shadowLock) && rho_lock.lockset.PCPlocks.get(n_k).PCPthreads.contains(T)) {
				rho_lock.lockset.add(m_0, n_k);
			}
		}
	}
	
	private static void addCCPLock (LocksetVar rho_var, ShadowThread T, Lock m_0) {
		for (Lock n_k : rho_var.lockset.PCPlocks.keySet()) {
			if (!n_k.shadowLock.equals(m_0.shadowLock) && rho_var.lockset.PCPlocks.get(n_k).PCPthreads.contains(T)) {
				rho_var.lockset.add(m_0, n_k);
			}
		}
	}
	
	private static void addHBLock(LocksetLock rho_lock, ShadowThread T, Lock m_i) {
		if (rho_lock.lockset.containsExactly(T, Type.HB)) {
			if (GCPTool.Verbose == 1) Assert.assertTrue(!rho_lock.lockset.containsExactly(m_i, Type.CP), "Trying to add a lock to HBLockSet, but lock is already a member of lock CPLockSet");
			rho_lock.lockset.addHBLock(m_i, Type.HB, m_i.latestVersion, ((Lock)rho_lock).shadowLock.equals(m_i.shadowLock));
		}
	}
	
	private static void addHBLock(LocksetVar rho_var, ShadowThread T, Lock m_i) {
		if (rho_var.lockset.containsExactly(T, Type.HB)) {
			if (GCPTool.Verbose == 1) Assert.assertTrue(!rho_var.lockset.containsExactly(m_i, Type.CP), "Trying to add a lock to HBLockSet, but lock is already a member of var CPLockSet");
			rho_var.lockset.addHBLock(m_i, Type.HB, m_i.latestVersion, false);
		}
	}
	
	private static HashSet<LocksetLock> removeCCPElements(Var rho_var, Lock m_0) {
		HashSet<Lock> m_j_removal = new HashSet<Lock>();
		for (Lock m_j : rho_var.lockset.PCPlocks.keySet()) {
			if (m_j.shadowLock.equals(m_0.shadowLock)) {
				ShadowThread LHS_Wr_Xi = rho_var.lockset.PCPlocks.get(m_j).CCPWriteXiThread; 
				if (LHS_Wr_Xi != null) {
					if (GCPTool.Verbose == 1) assert rho_var.lockset.uniqueWriteThrModifier.uniqueCount > 0;
					rho_var.lockset.uniqueWriteThrModifier.uniqueCount--;
				}
				for (ShadowThread LHS_Rd_Xi : rho_var.lockset.PCPlocks.get(m_j).CCPReadXiThreads) {
					if (GCPTool.Verbose == 1) assert !rho_var.lockset.uniqueReadThr.isEmpty();
					if (GCPTool.Verbose == 1) assert rho_var.lockset.uniqueReadThr.containsKey(LHS_Rd_Xi);
					rho_var.lockset.uniqueReadThr.put(LHS_Rd_Xi, new ThrModifier(rho_var.lockset.uniqueReadThr.get(LHS_Rd_Xi).type, rho_var.lockset.uniqueReadThr.get(LHS_Rd_Xi).uniqueCount-1));
				}
				m_j_removal.add(m_j);
			}
		}
		for (Lock m_j : m_j_removal) {
			m_j.removalCount--;
			rho_var.lockset.PCPlocks.remove(m_j);
			if (printLockset) Util.log("removing ccp " + m_j.prettyName + " version: " + m_j.version + " for var: " + rho_var.prettyName + " version: " + rho_var.version);
		}
		rho_var.printLockSet(GCPEvents.out);
		return null;
	}
	
	private static HashSet<LocksetLock> removeCCPElements(Lock rho_lock, Lock m_0) {
		HashSet<Lock> m_j_removal = new HashSet<Lock>();
		for (Lock m_j : rho_lock.lockset.PCPlocks.keySet()) {
			if (m_j.shadowLock.equals(m_0.shadowLock)) {
				m_j_removal.add(m_j);
			}
		}
		for (Lock m_j : m_j_removal) {
			m_j.removalCount--;
			rho_lock.lockset.PCPlocks.remove(m_j);
			if (printLockset) Util.log("removing ccp " + m_j.prettyName + " version: " + m_j.version + " for lock: " + rho_lock.prettyName + " version: " + rho_lock.version);
		}
		rho_lock.printLockSet(GCPEvents.out);
		return null;
	}
}

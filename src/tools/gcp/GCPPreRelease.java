package tools.gcp;

import java.lang.ref.WeakReference;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import rr.state.ShadowLock;
import rr.state.ShadowThread;
import acme.util.Util;

public class GCPPreRelease {
	static void preRelease(ShadowThread T, Lock m_0) {
		Lock m_i = m_0;
		if (m_0.latestLock != null) {
			m_i = m_0.latestLock;
		}
		
		//PreCompute CCP elements that need to be transfered from current lock to some other lock that satisfies certain conditions
		TreeMap<Integer, HashMap<ShadowLock, Lock>> other_locks_Per_m_l = new TreeMap<Integer, HashMap<ShadowLock, Lock>>();
		int latest_m_satisfying_CP = -1;
		for (LocksetLock rho_lock : LocksetLock.locksetLocks) {
			Lock m_l = (Lock) rho_lock;
			if (m_l == null || m_l.shadowLock == null)
				continue;
			
			if (m_l.shadowLock.equals(m_i.shadowLock)) { //TODO: could be m_0?
				//Find earliest version of current lock. Used when triggering CCP according to Rule B 
				if (m_l.lockset.threads.containsKey(T) && m_l.lockset.threads.get(T).type == Type.CP) {
					if (m_l.version > latest_m_satisfying_CP)
						latest_m_satisfying_CP = m_l.version;
				}
				//Find all other locks(s) rhoprime elements will be transferred to from the current lock version l 
				if (!m_l.lockset.PCPlocks.isEmpty()) {
					HashMap<ShadowLock, Lock> other_locks = new HashMap<ShadowLock, Lock>();
					for (Lock n_k : m_l.lockset.PCPlocks.keySet()) {
						if (m_l.lockset.PCPlocks.get(n_k).PCPthreads.contains(T)) {
							if (!other_locks.containsKey(n_k.shadowLock) || n_k.version < other_locks.get(n_k.shadowLock).version) {
								other_locks.put(n_k.shadowLock, n_k);
							}
						}
					}
					other_locks_Per_m_l.put(m_l.version, other_locks);
				}
			}
		}
		
		//TODO: This shouldn't be needed since all elements a later lock could have should already be included in the earlier lock
		//TODO: Isn't needed if the correct index is returned below
		for (int l = m_i.version-2; l >= 0; l--) {
			HashMap<ShadowLock, Lock> other_locks = other_locks_Per_m_l.get(l);
			if (other_locks == null)
				other_locks = new HashMap<ShadowLock, Lock>();
			if (other_locks_Per_m_l.get(l+1) != null) {
				for (ShadowLock laterLockInstanceCCPElements : other_locks_Per_m_l.get(l+1).keySet()) {
					if (other_locks.containsKey(laterLockInstanceCCPElements)) {
						if (other_locks_Per_m_l.get(l+1).get(laterLockInstanceCCPElements).version < other_locks.get(laterLockInstanceCCPElements).version) {
							other_locks.put(laterLockInstanceCCPElements, other_locks_Per_m_l.get(l+1).get(laterLockInstanceCCPElements));
						}
					} else {
						other_locks.put(laterLockInstanceCCPElements, other_locks_Per_m_l.get(l+1).get(laterLockInstanceCCPElements));
					}
				}
			} else {
				other_locks_Per_m_l.put(l+1, new HashMap<ShadowLock, Lock>());
			}
			other_locks_Per_m_l.put(l, other_locks);
		}
		
		//PreRelease for all lock locksets
		for (LocksetLock rho_lock : LocksetLock.locksetLocks) {
			Set<Lock> initial_CCPLocks =  new HashSet<Lock>();
			for (Lock m_j : rho_lock.lockset.PCPlocks.keySet()) {
				initial_CCPLocks.add(m_j);
			}
			
			for (Lock m_j : initial_CCPLocks) {
				//TODO: exit look after finding the appropriate m_j? There should be only one version of any lock in a lockset right?
				if (m_j.shadowLock.equals(m_i.shadowLock)) {
					if (GCPTool.PrintStats)GCPStats.lastPreReleaseEvent++;
					//Trigger CCP according to Rule B
					if (m_j.version <= latest_m_satisfying_CP) {
						for (Lock LHS_Lock : rho_lock.lockset.PCPlocks.get(m_j).PCPlocks) {
							if (GCPTool.Verbose == 1) assert LHS_Lock.version == 0;
							rho_lock.lockset.addCPLock(LHS_Lock, ((Lock)rho_lock).shadowLock.equals(LHS_Lock.shadowLock));
						}
						for (ShadowThread LHS_Thr : rho_lock.lockset.PCPlocks.get(m_j).PCPthreads) {
							rho_lock.lockset.add(LHS_Thr, Type.CP);
							GCPTool.gcpThread.get(LHS_Thr).LockLocksetsContainingThr.add(rho_lock);
						}
						if (GCPTool.Verbose == 1) assert rho_lock.lockset.PCPlocks.get(m_j).CCPReadXiThreads.isEmpty(); //Lock locksets should not contain xi elements
						if (GCPTool.Verbose == 1) assert rho_lock.lockset.PCPlocks.get(m_j).CCPWriteXiThread == null;
					}
					
					//Transfer CCP to depend on other lock(s) for all l >= j
					if (!other_locks_Per_m_l.isEmpty()) {
//						for (int l = m_j.version; l <= other_locks_Per_m_l.lastKey(); l++) {
						int l = m_j.version;
							if (other_locks_Per_m_l.get(l) != null) {
								for (ShadowLock other_locks : other_locks_Per_m_l.get(l).keySet()) {
									Lock n_k = other_locks_Per_m_l.get(l).get(other_locks);
									for (Lock LHS_Lock : rho_lock.lockset.PCPlocks.get(m_j).PCPlocks) {
										rho_lock.lockset.add(LHS_Lock, n_k);
									}
									for (ShadowThread LHS_Thr : rho_lock.lockset.PCPlocks.get(m_j).PCPthreads) {
										rho_lock.lockset.add(LHS_Thr, n_k, false, false);
										GCPTool.gcpThread.get(LHS_Thr).LockLocksetsContainingThr.add(rho_lock);
									}
									if (GCPTool.Verbose == 1) assert rho_lock.lockset.PCPlocks.get(m_j).CCPReadXiThreads.isEmpty(); //Lock locksets should not contain xi elements
									if (GCPTool.Verbose == 1) assert rho_lock.lockset.PCPlocks.get(m_j).CCPWriteXiThread == null;
								}
							}
//						}
					}
					rho_lock.printLockSet(GCPEvents.out);
				}
			}
		}
		
		//PreRelease for all variable locksets
		for (LocksetVar rho_var : LocksetVar.locksetVars) {
			Set<Lock> initial_CCPLocks =  new HashSet<Lock>();
			for (Lock m_j : rho_var.lockset.PCPlocks.keySet()) {
				initial_CCPLocks.add(m_j);
			}
			
			for (Lock m_j : initial_CCPLocks) {
				//TODO: exit look after finding the appropriate m_j? There should be only one version of any lock in a lockset right?
				if (m_j.shadowLock.equals(m_i.shadowLock)) {
					if (GCPTool.PrintStats) GCPStats.lastPreReleaseEvent++;
					//Trigger CCP according to Rule B
					if (m_j.version <= latest_m_satisfying_CP) {
						for (Lock LHS_Lock : rho_var.lockset.PCPlocks.get(m_j).PCPlocks) {
							if (GCPTool.Verbose == 1) assert LHS_Lock.version == 0;
							rho_var.lockset.addCPLock(LHS_Lock, false);
						}
						for (ShadowThread LHS_Thr : rho_var.lockset.PCPlocks.get(m_j).PCPthreads) {
							rho_var.lockset.add(LHS_Thr, Type.CP);
							GCPTool.gcpThread.get(LHS_Thr).VarLocksetsContainingThr.add(rho_var);
						}
						ShadowThread LHS_Wr_Xi = rho_var.lockset.PCPlocks.get(m_j).CCPWriteXiThread; 
						if (LHS_Wr_Xi != null) {
							rho_var.lockset.setUniqueWriteThr(LHS_Wr_Xi, Type.CP, 0);
						}
						for (ShadowThread LHS_Rd_Xi : rho_var.lockset.PCPlocks.get(m_j).CCPReadXiThreads) {
							if (GCPTool.Verbose == 1) assert !rho_var.lockset.uniqueReadThr.containsKey(LHS_Rd_Xi);
							rho_var.lockset.setUniqueReadThr(LHS_Rd_Xi, Type.CP, 0);
						}
					}
					
					//Transfer CCP to depend on other lock(s)
					//TODO: Make sure the correct index is returned here
					if (!other_locks_Per_m_l.isEmpty()) {
//						for (int l = m_j.version; l <= other_locks_Per_m_l.lastKey(); l++) {
						int l = m_j.version;
							if (other_locks_Per_m_l.get(l) != null) {
								for (ShadowLock other_locks : other_locks_Per_m_l.get(l).keySet()) {
									Lock n_k = other_locks_Per_m_l.get(l).get(other_locks);
									for (Lock LHS_Lock : rho_var.lockset.PCPlocks.get(m_j).PCPlocks) {
										rho_var.lockset.add(LHS_Lock, n_k);
									}
									for (ShadowThread LHS_Thr : rho_var.lockset.PCPlocks.get(m_j).PCPthreads) {
										rho_var.lockset.add(LHS_Thr, n_k, false, false);
										GCPTool.gcpThread.get(LHS_Thr).VarLocksetsContainingThr.add(rho_var);
									}
									ShadowThread LHS_Wr_Xi = rho_var.lockset.PCPlocks.get(m_j).CCPWriteXiThread;
									if (LHS_Wr_Xi != null) {
										rho_var.lockset.add(LHS_Wr_Xi, n_k, true, true);
										n_k.VarResolvingConditionalCP.add(rho_var);
										GCPTool.gcpThread.get(LHS_Wr_Xi).VarLocksetsContainingThr.add(rho_var);
									}
									for (ShadowThread LHS_Rd_Xi : rho_var.lockset.PCPlocks.get(m_j).CCPReadXiThreads) {
										rho_var.lockset.add(LHS_Rd_Xi, n_k, true, false);
										n_k.VarResolvingConditionalCP.add(rho_var);
										GCPTool.gcpThread.get(LHS_Rd_Xi).VarLocksetsContainingThr.add(rho_var);
									}
								}
							}
//						}
					}
					rho_var.printLockSet(GCPEvents.out);
				}
			}
		}
	}
}

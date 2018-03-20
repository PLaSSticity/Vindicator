package tools.gcp;

import java.util.HashSet;
import java.util.Set;

import rr.state.ShadowThread;

class PCPLock {
	Lock RHS_Lock; 
	Set<Lock> PCPlocks = new HashSet<Lock>();
	Set<ShadowThread> PCPthreads = new HashSet<ShadowThread>();
	ShadowThread CCPWriteXiThread = null;
	Set<ShadowThread> CCPReadXiThreads = new HashSet<ShadowThread>();
	
	PCPLock(Lock LHS_Lock, Lock RHS_Lock) {
		this.RHS_Lock = RHS_Lock;
		PCPlocks.add(LHS_Lock);
	}
	
	PCPLock(ShadowThread LHS_T, Lock RHS_Lock, boolean isUnique, boolean isWriteUnique) {
		this.RHS_Lock = RHS_Lock;
		PCPthreads.add(LHS_T);
		if (isUnique) {
			if (isWriteUnique) {
				CCPWriteXiThread = LHS_T;
			} else {
				CCPReadXiThreads.add(LHS_T);
			}
		}
	}
	
	public String toString() {
		String s = "";
		String delim = "";
		for (Lock lock : PCPlocks) {
			s += delim + "\\PCPLock{"+ lock + "}{" + RHS_Lock + "}{" + RHS_Lock.version + "}";
			delim = ", ";
		}
		for (ShadowThread thr : PCPthreads) {
			s += delim + "\\PCPThread{" + "T" + thr.getTid() + "}{"+ RHS_Lock + "}{" + RHS_Lock.version + "}";
			delim = ", ";
		}
		for (ShadowThread readXiThr : CCPReadXiThreads) {
			s += delim + "\\PCPThread{" + "Rd$\\xi$" + readXiThr.getTid() + "}{"+ RHS_Lock + "}{" + RHS_Lock.version + "}";
			delim = ", ";
		}
		if (CCPWriteXiThread != null) {
			s += delim + "\\PCPThread{" + "Wr$\\xi$" + CCPWriteXiThread.getTid() + "}{"+ RHS_Lock + "}{" + RHS_Lock.version + "}";
			delim = ", ";
		}
		return s;
	}
}
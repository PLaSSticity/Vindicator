package tools.gcp;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import acme.util.Util;

import rr.state.ShadowLock;
import rr.state.ShadowThread;

class Lock extends LocksetLock {
	ShadowThread heldByThread;
	int latestVersion;
	private int lockID;
	Lock latestLock;
	ShadowLock shadowLock;
	int removalCount = 0; 
	Set<LocksetVar> VarResolvingConditionalCP = new HashSet<LocksetVar>();
	
	Lock(ShadowLock lock) {
		super(lock);
		if (lock == null) {
			if (GCPTool.PrintStats) GCPStats.artificialLockLocksetCreation++;
			if (GCPTool.Verbose == 2)
				Util.log("artificial lock: " + this.name + " version: " + this.version + " created");
		} else {
			if (GCPTool.PrintStats) GCPStats.lockLocksetCreation++;
			if (GCPTool.Verbose == 2)
				Util.log("lock: " + this.name + " version: " + this.version + " created");
		}
		shadowLock = lock;
		this.lockID = lock.hashCode();
	}
	
	protected void finalize() throws Throwable {
		try {
			if (shadowLock == null) {
				if (GCPTool.PrintStats) GCPStats.artificialLockLocksetDestroyed++;
				if (GCPTool.Verbose == 2)
					Util.log("artifical lock: " + this.name + " version: " + this.version + " finalized");
			} else {
				if (GCPTool.PrintStats) GCPStats.lockLocksetDestroyed++;
				if (GCPTool.Verbose == 2)
					Util.log("lock: " + this.name + " version: " + this.version + " finalized");
			}
			for (ShadowLock locks : this.lockset.locks.keySet()) {
				GCPTool.gcpLock.get(locks).removalCount--;
			}
			for (Lock RHSCCPLock : this.lockset.PCPlocks.keySet()) {
				RHSCCPLock.removalCount--;
			}
		} catch(Throwable t) {
			t.printStackTrace();
		} finally {
			super.finalize();
		}
	}
	
	Lock(ShadowLock lock, int version, int lockID) {
		super(lock, version, lockID);
		if (lock == null) {
			if (GCPTool.PrintStats) GCPStats.artificialLockLocksetCreation++;
			if (GCPTool.Verbose == 2)
				Util.log("artificial lock: " + this.name + " version: " + this.version + " created");
		} else {
			if (GCPTool.PrintStats) GCPStats.lockLocksetCreation++;
			if (GCPTool.Verbose == 2)
				Util.log("lock: " + this.name + " version: " + this.version + " created");
		}
		shadowLock = lock;
		this.latestVersion = version;
		this.lockID = lockID;
		if (!GCPTool.HBPure) {
			LocksetLock.locksetLocks.add(this);
		}
	}
	
	Lock acq(ShadowThread thr, ShadowLock lock, int lockID) {
		latestVersion++;
		Lock m_i = new Lock(lock, latestVersion, lockID);
		m_i.heldByThread = thr;
		return m_i;
	}
	
	void rel() {
		latestLock = null;
		heldByThread = null;
	}
	
	public int getLockID() {
		return lockID;
	}
}
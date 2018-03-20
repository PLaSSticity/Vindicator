package tools.gcp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

import acme.util.Util;

import rr.state.ShadowLock;
import rr.state.ShadowThread;

class Lockset {
	WeakHashMap<ShadowLock, LockModifier> locks = new WeakHashMap<ShadowLock, LockModifier>();
	HashMap<ShadowThread,ThrModifier> threads = new HashMap<ShadowThread,ThrModifier>();
	HashMap<Lock,PCPLock> PCPlocks = new HashMap<Lock,PCPLock>();
	boolean isVar;
	boolean [] updateTypes = new boolean[3]; //HB/LS, CP, and CCP
	ShadowThread uniqueWriteThr = null;
	ThrModifier uniqueWriteThrModifier = null;
	HashMap<ShadowThread, ThrModifier> uniqueReadThr = new HashMap<ShadowThread, ThrModifier>();
	
	public Lockset(boolean isVar) {
		this.isVar = isVar;
	}
	
	boolean isVar() {
		return isVar;
	}
	
	void addCPLock(Lock lock, boolean same_lock) {
		if (locks.containsKey(lock.shadowLock)) {
			if (locks.get(lock.shadowLock).type == Type.HB || locks.get(lock.shadowLock).type == Type.LS) {
				if (!same_lock) lock.removalCount--;
				locks.put(lock.shadowLock, new LockModifier(Type.CP, lock));
				setUpdateTypes(Type.CP);
			}
		} else {
			locks.put(lock.shadowLock, new LockModifier(Type.CP, lock));
			setUpdateTypes(Type.CP);
		}
	}

	void addHBLock(Lock lock, Type type, int lockVersion, boolean same_lock) {
		if (locks.containsKey(lock.shadowLock)){
			if(locks.get(lock.shadowLock).type != Type.CP && lockVersion < locks.get(lock.shadowLock).getLockVersion()) {
				locks.put(lock.shadowLock, new LockModifier(type, lock, lockVersion));
				setUpdateTypes(type);
			}
		} else {
			locks.put(lock.shadowLock, new LockModifier(type, lock, lockVersion));
			if (!same_lock) lock.removalCount++;
			setUpdateTypes(type);
		}
	}

	void add(ShadowThread thr, Type type) {
		if (!threads.containsKey(thr) || threads.get(thr).type.compareTo(type) < 0) {
			threads.put(thr, new ThrModifier(type));
			setUpdateTypes(type);
		}
	}
	
	void add(Lock LHS_Lock, Lock RHS_Lock) {
		if (!PCPlocks.containsKey(RHS_Lock)) {
			RHS_Lock.removalCount++;
			PCPlocks.put(RHS_Lock, new PCPLock(LHS_Lock, RHS_Lock));
		} else {
			PCPlocks.get(RHS_Lock).PCPlocks.add(LHS_Lock);
		}
		setUpdateTypes(Type.PCP);
	}
	
	void add(ShadowThread LHS_T, Lock RHS_Lock, boolean isUnique, boolean isWriteUnique) {
		if (!PCPlocks.containsKey(RHS_Lock)) {
			RHS_Lock.removalCount++;
			PCPlocks.put(RHS_Lock, new PCPLock(LHS_T, RHS_Lock, isUnique, isWriteUnique));
		} else {
			//TODO: If unique thread, then add as both unique and not unique thread?
			PCPlocks.get(RHS_Lock).PCPthreads.add(LHS_T);
			if (isUnique) {
				if (isWriteUnique) {
					PCPlocks.get(RHS_Lock).CCPWriteXiThread = LHS_T;
				} else {
					PCPlocks.get(RHS_Lock).CCPReadXiThreads.add(LHS_T);
				}
			}
		}
		setUpdateTypes(Type.PCP);
	}

	boolean containsExactly(Lock lock, Type type) {
		if (locks.get(lock.shadowLock) == null)
			return false;
		LockModifier modifier = locks.get(lock.shadowLock);
		if (modifier == null) {
			return false;
		}
		return modifier.type == type;
	}

	boolean containsExactly(ShadowThread thr, Type type) {
		if (GCPTool.Verbose == 1) assert type != Type.LS; //Thread should never appear as an LS element in any lockset
		ThrModifier modifier = threads.get(thr);
		if (modifier == null) {
			return false;
		}
		return modifier.type == type;
	}
	
	void setUniqueWriteThr(ShadowThread thr, Type type, int count) {
		this.uniqueWriteThr = thr;
		this.uniqueWriteThrModifier = new ThrModifier(type, count);
	}
	
	void setUniqueReadThr(ShadowThread thr, Type type, int count) {
		this.uniqueReadThr.put(thr, new ThrModifier(type, count));
	}
	
	void clear() {
		locks.clear();
		threads.clear();
		PCPlocks.clear();
		uniqueWriteThr = null;
		uniqueWriteThrModifier = null;
		uniqueReadThr.clear();
		resetUpdateTypes();
	}
	
	void resetUpdateTypes() {
		for (int i = 0; i < updateTypes.length; i++) {
			updateTypes[i] = false;
		}
	}
	
	void setUpdateTypes(Type type) {
		switch(type) {
		case HB: updateTypes[0] = true; break;
		case LS: updateTypes[0] = true; break;
		case CP: updateTypes[1] = true; break;
		case PCP: updateTypes[2] = true; break;
		default: assert false;
		}
	}

	boolean isEmpty() {
		return locks.isEmpty() && threads.isEmpty() && PCPlocks.isEmpty() && uniqueWriteThr == null && uniqueWriteThrModifier == null && uniqueReadThr.isEmpty();
	}

	public String toString() {
		String s = "\\{";
		String delim = "";
		for (ShadowLock lockIDs : locks.keySet()) {
			Id id = locks.get(lockIDs).getLockInstance();
			s += delim;
			switch(locks.get(lockIDs).type) {
			case HB: s += "\\HBLock{" + id + "}{" + locks.get(lockIDs).getLockVersion() + "}"; break;
			case LS: s += "\\LSLock{" + id + "}{" + locks.get(lockIDs).getLockVersion() + "}"; break;
			case CP: s += "\\CPLock{" + id + "}"; break;
			default: assert false;
			}
			delim = ", ";
		}
		for (ShadowThread id : threads.keySet()) {
			s += delim;
			switch(threads.get(id).type) {
			case HB: s += "\\HBThread{" + "T" + id.getTid() + "}"; break;
			case CP: s += "\\CPThread{" + "T" + id.getTid() + "}"; break;
			default: assert false;
			}
			delim = ", ";
		}		
		if (uniqueWriteThrModifier != null) {
			switch(uniqueWriteThrModifier.type) {
			case HB: s += delim + "\\HBThread{" + "$\\xi$" + uniqueWriteThr.getTid() + "}"; delim = ", "; break;
			case CP: s += delim + "\\CPThread{" + "$\\xi$" + uniqueWriteThr.getTid() + "}"; delim = ", "; break;
			default: assert false;
			}
		}
		for (Id id : PCPlocks.keySet()) {
			s += delim + PCPlocks.get(id);
			delim = ", ";
		}
		s += "\\}";
		return s;
	}
	
	public String toStringType(Type type) {
		String s = "\\{";
		String delim = "";
		for (ShadowLock lockIDs : locks.keySet()) {
			Id id = locks.get(lockIDs).getLockInstance();
			if (locks.get(lockIDs).type.equals(type) || (locks.get(lockIDs).type.equals(Type.LS) && type.equals(Type.HB))) {
				switch(locks.get(lockIDs).type) {
				case HB: s += delim + "\\HBLock{" + id + "}{" + locks.get(lockIDs).getLockVersion() + "}"; delim = ", "; break;
				case LS: s += delim + "\\LSLock{" + id + "}{" + locks.get(lockIDs).getLockVersion() + "}"; delim = ", "; break;
				case CP: s += delim + "\\CPLock{" + id + "}"; delim = ", "; break;
				default: assert false;
				}
			}
		}
		for (ShadowThread id : threads.keySet()) {
			if (threads.get(id).type.equals(type)) {
				switch(threads.get(id).type) {
				case HB: s += delim + "\\HBThread{" + "T" + id.getTid() + "}"; delim = ", "; break;
				case CP: s += delim + "\\CPThread{" + "T" + id.getTid() + "}"; delim = ", "; break;
				default: assert false;
				}
			}
		}
		if (uniqueWriteThrModifier != null && uniqueWriteThrModifier.type == type) {
			switch(type) {
			case HB: s += delim + "\\HBThread{" + "$\\xi$" + uniqueWriteThr.getTid() + "}"; delim = ", "; break;
			case CP: s += delim + "\\CPThread{" + "$\\xi$" + uniqueWriteThr.getTid() + "}"; delim = ", "; break;
			default: assert false;
			}
		}
		if (type.equals(Type.PCP)) {
			for (Id id : PCPlocks.keySet()) {
				s += delim + PCPlocks.get(id);
				delim = ", ";
			}
		}
		s += "\\}";
		return s;
	}
}
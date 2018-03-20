package tools.gcp;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import rr.state.ShadowThread;

public class Thr {
	
	Set<LocksetVar> VarLocksetsContainingThr =	Collections.newSetFromMap(new WeakHashMap<LocksetVar, Boolean>());
	Set<LocksetLock> LockLocksetsContainingThr = Collections.newSetFromMap(new WeakHashMap<LocksetLock, Boolean>());
	
	Thr(ShadowThread thr) { 
		
	}

}

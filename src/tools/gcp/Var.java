package tools.gcp;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.WeakHashMap;

import acme.util.Util;

import rr.event.AccessEvent;
import rr.event.Event;
import rr.event.JoinEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;

abstract class Var extends LocksetVar implements ShadowVar {
	
	static final ShadowThread allThr = null;
	
	//Every instance:
	ShadowThread lastAccessedByThread;
	private Event event;
	Event nextWrite_Event;
	long nextWrite_Dist;
	HashMap<ShadowThread, Event> nextRead_Event = new HashMap<ShadowThread, Event>();
	HashMap<ShadowThread, Long> nextRead_Dist = new HashMap<ShadowThread, Long>();
	long totalEventOrder = -1;
	private Lock volatileLock;
	private Lock classInitLock;
	boolean classInit = false;
	boolean classInitRemoveLock = false;
	
	//For Write x_i to search backwards for all x_h when applying Rule A:
	WeakReference<WriteVar> priorWriteInstance;
	WeakHashMap<ShadowThread, ReadVar> sameIndexReadInstance = new WeakHashMap<ShadowThread, ReadVar>();
	
	//Only initial instance [index 0]:
	WriteVar latestInstance;
	
	Var(Event var) {
		super(var, 0, false);
		this.event = var;
		this.version = 0;
		if (var instanceof VolatileAccessEvent) {
			this.volatileLock = new Lock(new ShadowLock(new Object()), 0, ((VolatileAccessEvent)var).getAccessInfo().getId());
		}
		this.lastAccessedByThread = allThr;
	}
	
	Var(Event var, ShadowThread thr) {
		super(var, thr, 0, false);
		this.event = var;
		this.version = 0;
		if (var instanceof VolatileAccessEvent) {
			this.volatileLock = new Lock(new ShadowLock(new Object()), 0, ((VolatileAccessEvent)var).getAccessInfo().getId());
		}
		this.lastAccessedByThread = allThr;
	}
	
	Var(Event var, int version, boolean clone) {
		super(var, version, clone);
		this.event = var;
	}
	
	Var(Event var, ShadowThread thr, int version, boolean clone) {
		super(var, thr, version, clone);
		this.event = var;
	}
	
	public Event getEvent() {
		return event;
	}
	
	public Lock getVolatileLock() {
		return volatileLock;
	}
	
	public void setVolatileLock(Lock volatileLock) {
		this.volatileLock = volatileLock;
	}
	
	public Lock getClassInitLock() {
		return classInitLock;
	}
	
	public void setClassInitLock(Lock classInitLock) {
		this.classInitLock = classInitLock;
	}
	
	public static String toString(final ShadowThread td) {
		return String.format("[tid=%-2d]", td.getTid());
	}
	
	public void setNextWriteInstance(Event event, long eventOrder) {
		this.nextWrite_Event = event;
		this.nextWrite_Dist = eventOrder;
	}
	
	public void setNextReadInstance(Event event, long eventOrder, ShadowThread T) {
		this.nextRead_Event.put(T, event);
		this.nextRead_Dist.put(T, eventOrder);
	}
}

class WriteVar extends Var {
	WriteVar(AccessEvent var) {
		super(var);
		if (GCPTool.Verbose == 2)
			Util.log("construct write var: " + this.name + " version: " + this.version + " hash: " + this.hashCode());
		if (GCPTool.PrintStats)GCPStats.writeLocksetCreation++;
	}
	
	protected void finalize() throws Throwable {
		try {
			if (GCPTool.Verbose == 2)
				Util.log("finalize write var: " + this.name + " version: " + this.version + " hash: " + this.hashCode());
			if (GCPTool.PrintStats) GCPStats.writeLocksetDestroyed++;
			for (ShadowLock locks : this.lockset.locks.keySet()) {
				GCPTool.gcpLock.get(locks).removalCount--;
			}
			for (Lock RHSCCPLock : this.lockset.PCPlocks.keySet()) {
				RHSCCPLock.removalCount--;
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			super.finalize();
		}
	}
	
	WriteVar(StartEvent var) {
		super(var);
		if (GCPTool.PrintStats) GCPStats.writeLocksetCreation++;
	}
	
	WriteVar(JoinEvent var) {
		super(var);
		if (GCPTool.PrintStats) GCPStats.writeLocksetCreation++;
	}
	
	WriteVar(Event var, int version, boolean clone) {
		super(var, version, clone);
		if (GCPTool.Verbose == 2)
			Util.log("construct write var: " + this.name + " version: " + this.version + " hash: " + this.hashCode());
		if (GCPTool.PrintStats) GCPStats.writeLocksetCreation++;
	}
	
	WriteVar(StartEvent var, int version, boolean clone) {
		super(var, version, clone);
		if (GCPTool.PrintStats) GCPStats.writeLocksetCreation++;
	}
	
	WriteVar(JoinEvent var, int version, boolean clone) {
		super(var, version, clone);
		if (GCPTool.PrintStats) GCPStats.writeLocksetCreation++;
	}
	
	WriteVar wri(ShadowThread T, int version, Event event, long eventOrder) {
		this.setNextWriteInstance(event, eventOrder);
		
		WriteVar x_i = new WriteVar(event, version, false);
		x_i.lastAccessedByThread = T;
		if (event instanceof VolatileAccessEvent) {
			x_i.setVolatileLock(this.getVolatileLock());
		}
		x_i.priorWriteInstance = new WeakReference<WriteVar>(this);
		return x_i;
	}
}

class ReadVar extends Var {
	ReadVar(AccessEvent var) {
		super(var, null);
		if (GCPTool.Verbose == 2)
			Util.log("construct read var: " + this.name + " version: " + this.version + " hash: " + this.hashCode());
		if (GCPTool.PrintStats) GCPStats.readLocksetCreation++;
	}
	
	protected void finalize() throws Throwable {
		try {
			if (GCPTool.Verbose == 2)
				Util.log("finalize read var: " + this.name + " version: " + this.version + " hash: " + this.hashCode());
			if (GCPTool.PrintStats) GCPStats.readLocksetDestroyed++;
			for (ShadowLock locks : this.lockset.locks.keySet()) {
				GCPTool.gcpLock.get(locks).removalCount--;
			}
			for (Lock RHSCCPLock : this.lockset.PCPlocks.keySet()) {
				RHSCCPLock.removalCount--;
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			super.finalize();
		}
	}
	
	ReadVar(Event var, ShadowThread thr, int version, boolean clone) {
		super(var, thr, version, clone);
		if (GCPTool.Verbose == 2)
			Util.log("construct read var: " + this.name + " version: " + this.version + " hash: " + this.hashCode());
		if (GCPTool.PrintStats) GCPStats.readLocksetCreation++;
	}
}
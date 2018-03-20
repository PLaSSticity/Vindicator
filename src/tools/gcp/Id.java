package tools.gcp;

import acme.util.Util;
import rr.event.AccessEvent;
import rr.event.ArrayAccessEvent;
import rr.event.Event;
import rr.event.FieldAccessEvent;
import rr.event.JoinEvent;
import rr.event.StartEvent;
import rr.state.ShadowLock;
import rr.state.ShadowThread;

public abstract class Id {
	String name;
	String prettyName;
	int version;
	
	public Id(Event variable, int version) {
		if (variable instanceof AccessEvent) {
			AccessEvent var = (AccessEvent) variable;
			if (var instanceof FieldAccessEvent) {
				try {
					this.prettyName = ((FieldAccessEvent)var).getInfo().getField().getName();
				} catch (NullPointerException e) {
					this.prettyName = "MissingInfo" + var.hashCode();
				}
			} else if (var instanceof ArrayAccessEvent) {
				this.prettyName = Util.objectToIdentityString(var.getTarget()) + "[" + ((ArrayAccessEvent)var).getIndex() + "]";
			}
			
		} else if (variable instanceof StartEvent) {
			StartEvent var = (StartEvent) variable;
			this.prettyName = "T" + var.getThread().getTid() + "->T" + var.getNewThread().getTid();
			
		} else if (variable instanceof JoinEvent) {
			JoinEvent var = (JoinEvent) variable;
			this.prettyName = "T" + var.getThread().getTid() + "->T" + var.getJoiningThread().getTid();
		}
		this.name = version+prettyName;
		this.version = version;
	}
	
	public Id(Event variable, ShadowThread thr, int version) {
		if (variable instanceof AccessEvent) {
			AccessEvent var = (AccessEvent) variable;
			if (var instanceof FieldAccessEvent) {
				try {
					this.prettyName = ((FieldAccessEvent)var).getInfo().getField().getName();
				} catch (NullPointerException e) {
					this.prettyName = "MissingInfo" + var.hashCode();
				}
			} else if (var instanceof ArrayAccessEvent) {
				this.prettyName = Util.objectToIdentityString(var.getTarget()) + "[" + ((ArrayAccessEvent)var).getIndex() + "]";
			}
			
		} else if (variable instanceof StartEvent) {
			StartEvent var = (StartEvent) variable;
			this.prettyName = "T" + var.getThread().getTid() + "->T" + var.getNewThread().getTid();
			
		} else if (variable instanceof JoinEvent) {
			JoinEvent var = (JoinEvent) variable;
			this.prettyName = "T" + var.getThread().getTid() + "->T" + var.getJoiningThread().getTid();
		}
		this.name = version+"T"+thr.getTid()+prettyName;
		this.version = version;
	}
	
	public Id(ShadowLock lock) {
		if (GCPTool.Verbose == 1) assert lock != null;
		this.prettyName = Util.objectToIdentityString(lock.getLock());
		this.name = prettyName;
	}
	
	public Id(ShadowLock lock, int version, int lockID) {
		if (lock == null) {
			this.prettyName = "PL" + lockID;
		} else {
			this.prettyName = Util.objectToIdentityString(lock.getLock());			
		}
		this.name = version+prettyName;
		this.version = version;
	}
	
	public String toString() {
		return prettyName;
	}
	
	public int getVersion() {
		return version;
	}
}
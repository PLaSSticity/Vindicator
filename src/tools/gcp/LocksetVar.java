package tools.gcp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

import acme.util.Assert;
import acme.util.Util;
import rr.event.AccessEvent;
import rr.event.Event;
import rr.event.FieldAccessEvent;
import rr.event.JoinEvent;
import rr.event.StartEvent;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.tool.RR;

abstract class LocksetVar extends Id {
	static Set<LocksetVar> locksetVars = Collections.newSetFromMap(new WeakHashMap<LocksetVar, Boolean>());
	static final boolean printLockset = RR.raptorPrintLocksetOption.get();
	static final boolean printLatexTrace = RR.raptorPrintLatexOption.get();

	Lockset lockset;
	
	LocksetVar(Event var, int version, boolean clone) {
		super(var, version);
		if (!clone) {
			this.lockset = new Lockset(true);
			locksetVars.add(this);
		}
	}
	
	LocksetVar(Event var, ShadowThread thr, int version, boolean clone) {
		super(var, thr, version);
		if (!clone) {
			this.lockset = new Lockset(true);
			locksetVars.add(this);
		}
	}
	
	Lockset getLockset() {
		return lockset;
	}
	
	boolean printLockSet(BufferedWriter out) {
		if (printLockset && (this.getLockset().updateTypes[0] || this.getLockset().updateTypes[1] || this.getLockset().updateTypes[2])) {
			if (this instanceof WriteVar) {
				Util.message("  WriteLS(" + this + ")(" + this.version + ") = " + lockset);
			} else if(this instanceof ReadVar) {
				Util.message("  ReadLS(" + this + ")(" + this.version + "T" + ((Var)this).lastAccessedByThread.getTid() + ") = " + lockset);
			}
		}
		if (printLatexTrace && !(this.prettyName.contains("->") || this.prettyName.equals("PseudoLock"))) {
			try {
				String delim = "";
				if (this.getLockset().updateTypes[0]) {
					delim = "";
					for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
						delim += "&";
					}
					out.write(delim + " \\HBLockSet{" + this + "}{" + this.version + "} = " + lockset.toStringType(Type.HB));
					out.write("\\\\\n");
				}
				if (this.getLockset().updateTypes[1]) {
					delim = "";
					for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
						delim += "&";
					}
					out.write(delim + " \\CPLockSet{" + this + "}{" + this.version + "} = " + lockset.toStringType(Type.CP));
					out.write("\\\\\n");
				}
				if (this.getLockset().updateTypes[2]) {
					delim = "";
					for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
						delim += "&";
					}
					out.write(delim + " \\PCPLockSet{" + this + "}{" + (this.version > 0 ? this.version : "") + "} = " + lockset.toStringType(Type.PCP));
					out.write("\\\\\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		this.getLockset().resetUpdateTypes();
		return true;
	}
	
	boolean printLockSet(BufferedWriter out, boolean periodicPrint) {
		if (periodicPrint || (printLockset && (this.getLockset().updateTypes[0] || this.getLockset().updateTypes[1] || this.getLockset().updateTypes[2]))) {
			if (this instanceof WriteVar) {
				Util.message("  WriteLS(" + this + ")(" + this.hashCode() + ")(" + ((AccessEvent)((Var)this).getEvent()).getOriginalShadow() + ")(" + this.version + ") = " + lockset);
			} else if(this instanceof ReadVar) {
				Util.message("  ReadLS(" + this + ")(" + this.hashCode() + ")(" + ((AccessEvent)((Var)this).getEvent()).getOriginalShadow() + ")(" + this.version + "T" + ((Var)this).lastAccessedByThread.getTid() + ") = " + lockset);
			}
		}
		return true;
	}
}
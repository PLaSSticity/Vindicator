package tools.gcp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.WeakHashMap;

import acme.util.Assert;
import acme.util.Util;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.tool.RR;

abstract class LocksetLock extends Id {
	static Set<LocksetLock> locksetLocks = Collections.newSetFromMap(new WeakHashMap<LocksetLock, Boolean>());
	static final boolean printLockset = RR.raptorPrintLocksetOption.get();
	static final boolean printLatexTrace = RR.raptorPrintLatexOption.get();
	
	Lockset lockset;

	LocksetLock(ShadowLock lock) {
		super(lock);
	}
	
	LocksetLock(ShadowLock lock, int version, int lockID) {
		super(lock, version, lockID);
		if (!GCPTool.HBPure) {
			this.lockset = new Lockset(false);
		}
	}
	
	Lockset getLockset() {
		return lockset;
	}
	
	boolean printLockSet(BufferedWriter out) {
		if (printLockset && (this.getLockset().updateTypes[0] || this.getLockset().updateTypes[1] || this.getLockset().updateTypes[2])) {
			Util.message("  LockLS(" + this + ")(" + this.version + ") = " + lockset);
		}
		if (printLatexTrace && !(this.prettyName.contains("->") || this.prettyName.contains("PseudoLock"))) {
			try {
				String delim = "";
				if (this.getLockset().updateTypes[0]) {
					delim = "";
					for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
						delim += "&";
					}
					out.write(delim + " \\HBLockSet{" + this.prettyName + "}{" + (this.version > 0 ? this.version : "") + "} = " + lockset.toStringType(Type.HB));
					out.write("\\\\\n");
				}
				if (this.getLockset().updateTypes[1]) {
					delim = "";
					for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
						delim += "&";
					}
					out.write(delim + " \\CPLockSet{" + this.prettyName + "}{" + (this.version > 0 ? this.version : "") + "} = " + lockset.toStringType(Type.CP));
					out.write("\\\\\n");
				}
				if (this.getLockset().updateTypes[2]) {
					delim = "";
					for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
						delim += "&";
					}
					out.write(delim + " \\PCPLockSet{" + this.prettyName + "}{" + (this.version > 0 ? this.version : "") + "} = " + lockset.toStringType(Type.PCP));
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
			Util.message("  LockLS(" + this + ")(" + this.hashCode() + ")(" + this.version + ") = " + lockset);
		}
		return true;
	}
}
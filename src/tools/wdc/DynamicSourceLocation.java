package tools.wdc;

import rr.event.AccessEvent;
import rr.meta.MethodInfo;
import rr.meta.SourceLocation;

class DynamicSourceLocation {
	
	final SourceLocation loc;
	final MethodInfo eventMI;
	final EventNode eventNode;
	
	public DynamicSourceLocation(AccessEvent ae, EventNode eventNode, MethodInfo eventMI) {
		this(ae.getAccessInfo().getLoc(), eventNode, eventMI);
	}
	
	public DynamicSourceLocation(SourceLocation loc, EventNode eventNode, MethodInfo eventMI) {
		this.loc = loc;
		this.eventMI = eventMI;
		this.eventNode = eventNode;
	}
	
	// No need to do lazy merging with eager merging enabled
	/*
	@Override
	public void finalize() {
		// Now it should be safe to see if the referenced can be merged with its successor
		synchronized (WDCTool.event_lock) { // finalizers can run concurrently with other code (and with each other?)
			if (eventNode instanceof RdWrNode) {
				((RdWrNode)eventNode).tryToMergeNodeWithSuccessor();
			}
		}
	}
	*/
	
	@Override
	public String toString() {
		return loc + " (event " + eventNode + ")";
	}

}

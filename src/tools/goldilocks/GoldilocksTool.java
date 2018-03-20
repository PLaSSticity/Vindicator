/******************************************************************************

Copyright (c) 2016, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.  

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

 * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 ******************************************************************************/


package tools.goldilocks;

import rr.RRMain;
import rr.annotations.Abbrev;
import rr.barrier.BarrierEvent;
import rr.barrier.BarrierListener;
import rr.error.ErrorMessage;
import rr.error.ErrorMessages;
import rr.event.AccessEvent;
import rr.event.AccessEvent.Kind;
import rr.event.AcquireEvent;
import rr.event.ArrayAccessEvent;
import rr.event.ClassAccessedEvent;
import rr.event.ClassInitializedEvent;
import rr.event.FieldAccessEvent;
import rr.event.JoinEvent;
import rr.event.NewThreadEvent;
import rr.event.ReleaseEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.event.WaitEvent;
import rr.instrument.classes.ArrayAllocSiteTracker;
import rr.meta.ArrayAccessInfo;
import rr.meta.ClassInfo;
import rr.meta.FieldInfo;
import rr.meta.MetaDataInfoMaps;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.state.ShadowVolatile;
import rr.tool.RR;
import rr.tool.Tool;

import acme.util.Assert;
import acme.util.Util;
import acme.util.count.AggregateCounter;
import acme.util.count.ThreadLocalCounter;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DecorationFactory.Type;
import acme.util.decorations.DefaultValue;
import acme.util.io.XMLWriter;
import acme.util.option.CommandLine;

/*
 *   - Properly replays events when the fast paths detect an error in all cases.
 *   - Handles tid reuse more precisely.
 */
@Abbrev("Goldilocks")
public class GoldilocksTool extends Tool implements BarrierListener<Object>  {

	static final boolean COUNT_OPERATIONS = RRMain.slowMode();

	public final ErrorMessage<FieldInfo> fieldErrors = ErrorMessages.makeFieldErrorMessage("Goldilocks");
	public final ErrorMessage<ArrayAccessInfo> arrayErrors = ErrorMessages.makeArrayErrorMessage("Goldilocks");

	// guarded by classInitTime
	@SuppressWarnings("serial")
	public static final Decoration<ClassInfo,GoldilocksVolatileState> classInitTime = MetaDataInfoMaps.getClasses().makeDecoration("Goldilocks:ClassInitTime", Type.MULTIPLE, 
			new DefaultValue<ClassInfo,GoldilocksVolatileState>() {
		public GoldilocksVolatileState get(ClassInfo st) {
			return new GoldilocksVolatileState(null);
		}
	});
	
	public static GoldilocksVolatileState getClassInitTime(ClassInfo ci) {
		synchronized(classInitTime) {
			return classInitTime.get(ci);
		}
	}

	public GoldilocksTool(final String name, final Tool next, CommandLine commandLine) {
		super(name, next, commandLine);
		/*
		new BarrierMonitor<FTBarrierState>(this, new DefaultValue<Object,FTBarrierState>() {
			public FTBarrierState get(Object k) {
				return new FTBarrierState(k, INIT_VECTOR_CLOCK_SIZE);
			}
		});
		*/
	}

	protected static GoldilocksThreadState ts_get_threadState(ShadowThread st) { Assert.panic("Bad"); return null; }
	protected static void ts_set_threadState(ShadowThread st, GoldilocksThreadState threadState) { Assert.panic("Bad");  }

	@SuppressWarnings("serial")
	static final Decoration<ShadowLock,GoldilocksLockState> lockStates = ShadowLock.makeDecoration("Goldilocks:ShadowLock", DecorationFactory.Type.MULTIPLE,
			new DefaultValue<ShadowLock,GoldilocksLockState>() { public GoldilocksLockState get(final ShadowLock shadowLock) { return new GoldilocksLockState(shadowLock); }});

	// only call when ld.peer() is held
	static final GoldilocksLockState getLockState(final ShadowLock shadowLock) {
		return lockStates.get(shadowLock);
	}

	@SuppressWarnings("serial")
	static final Decoration<ShadowVolatile,GoldilocksVolatileState> volatileStates = ShadowVolatile.makeDecoration("Goldilocks:shadowVolatile", DecorationFactory.Type.MULTIPLE,
			new DefaultValue<ShadowVolatile,GoldilocksVolatileState>() { public GoldilocksVolatileState get(final ShadowVolatile shadowVolatile) { return new GoldilocksVolatileState(shadowVolatile); }});

	// only call when we are in an event handler for the volatile field.
	protected static final GoldilocksVolatileState getVolatileState(final ShadowVolatile shadowVolatile) {
		return volatileStates.get(shadowVolatile);
	}

	@Override
	public void init() {
		Provenance.initFile();
	}
	
	@Override
	public void fini() {
		Provenance.reportUsage();
	}

	@Override
	public ShadowVar makeShadowVar(final AccessEvent event) {
		final ShadowThread st = event.getThread();
		if (event.getKind() == Kind.VOLATILE) {
			//final GoldilocksVolatileState volState = getV(((VolatileAccessEvent)event).getShadowVolatile());
			return super.makeShadowVar(event);
		} else {
			return new GoldilocksVarState(st, ts_get_threadState(st), event.isWrite(), event.getAccessInfo().getLoc());
		}
	}


	@Override
	public void create(NewThreadEvent event) {
		final ShadowThread st = event.getThread();
		ts_set_threadState(st, new GoldilocksThreadState(st));

		super.create(event);
	}

	@Override
	public void acquire(final AcquireEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksLockState lockState = getLockState(event.getLock());
		
		// Transfer m to T for all vars
		
		Transfer.addTransfer(lockState, threadState, event.getInfo().getLoc());

		super.acquire(event);
		if (COUNT_OPERATIONS) acquire.inc(st.getTid());
	}



	@Override
	public void release(final ReleaseEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksLockState lockState = getLockState(event.getLock());

		// Transfer T to m for all vars
		Transfer.addTransfer(threadState, lockState, event.getInfo().getLoc());

		super.release(event);
		if (COUNT_OPERATIONS) release.inc(st.getTid());
	}

	@Override
	public void access(final AccessEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final ShadowVar shadow = event.getOriginalShadow();
		
		if (shadow instanceof GoldilocksVarState) {
			GoldilocksVarState varState = (GoldilocksVarState)shadow;
			
			Object target = event.getTarget();
			if (target == null) {
				ClassInfo owner = ((FieldAccessEvent)event).getInfo().getField().getOwner();
				final GoldilocksVolatileState volatileState;
				
				synchronized (classInitTime) {
					volatileState = classInitTime.get(owner);
				}

				// Transfer v to T for all vars
				Transfer.addTransfer(volatileState, threadState, event.getAccessInfo().getLoc());
			}

			synchronized (varState) {
				
				if (event.isWrite()) {
					// Check for write-write races
					if (varState.univSet != null && !checkRaceFreedom(varState.univSet, threadState, st, false)) {
						varState.writeSet.syncElements.putAll(varState.univSet.syncElements);
						if (!checkRaceFreedom(varState.writeSet, threadState, st, true)) {
							error(event, varState, "Write-" + (event.isWrite() ? "write" : "read") + " race", "Unknown prev op", -1, "Access info: " + event.getAccessInfo(), st.getTid());
						}
					}
					// Check for read-write races
					for (ShadowThread su : varState.readSets.keySet()) {
						if (su != st) {
							SyncSet readSet = varState.readSets.get(su);
							if (varState.univSet != null) {
								readSet.syncElements.putAll(varState.univSet.syncElements);
							}
							if (!checkRaceFreedom(readSet, threadState, st, true)) {
								error(event, varState, "Read-write race", "Unknown prev op", -1, "Access info: " + event.getAccessInfo(), st.getTid());
							}
						}
					}
					// Update locksets
					varState.readSets.clear();
					varState.writeSet.clear();
					if (varState.univSet == null) {
						varState.univSet = SyncSet.makeSyncSet(threadState, st.getInnermostLock(), st, event.getAccessInfo().getLoc());
					} else {
						varState.univSet.resetRetainingHeldLocks(threadState, st.getInnermostLock(), st, event.getAccessInfo().getLoc());
					}
				} else {
					// Check for write-read races
					if (varState.univSet != null && !checkRaceFreedom(varState.univSet, threadState, st, false)) {
						varState.writeSet.syncElements.putAll(varState.univSet.syncElements);
						if (!checkRaceFreedom(varState.writeSet, threadState, st, true)) {
							error(event, varState, "Write-" + (event.isWrite() ? "write" : "read") + " race", "Unknown prev op", -1, "Access info: " + event.getAccessInfo(), st.getTid());
						}
					}
					// Update lockset
					// Don't add to read set if univ set contains needed element(s).
					// TODO: a general problem is whether read set should be empty vs. not even mapped in readSets depending on whether current access is covered by univSet
					//SyncSet readSet = varState.readSets.get(st);
					//if (readSet == null) {
						SyncSet readSet = SyncSet.makeSyncSet(varState.univSet != null && varState.univSet.hasSyncElement(threadState) ? null : threadState,
						                              (st.getInnermostLock() == null || (varState.univSet != null && varState.univSet.hasSyncElement(getLockState(st.getInnermostLock())))) ? null : st.getInnermostLock(),
						                              st, event.getAccessInfo().getLoc());
						if (readSet != null) {
							varState.readSets.put(st, readSet);
						} else {
							varState.readSets.remove(st);
						}
					/*} else {
						readSet.resetRetainingHeldLocks(varState.univSet.hasSyncElement(threadState) ? null : threadState,
						                                (st.getInnermostLock() == null || varState.univSet.hasSyncElement(getLockState(st.getInnermostLock()))) ? null : st.getInnermostLock(),
						                                st, event.getAccessInfo().getLoc());
					}*/
				}
				
				/*
				// Check for races
				if (!checkRaceFreedom(varState.univSet, threadState, st, false) ||
				    (event.isWrite() && !varState.readSets.isEmpty())) {
					if (!varState.univSet.isEmpty()) {
						varState.writeSet.syncElements.putAll(varState.univSet.syncElements);
						if (!checkRaceFreedom(varState.writeSet, threadState, st, true)) {
							error(event, varState, "Write-" + (event.isWrite() ? "write" : "read") + " race", "Unknown prev op", -1, "Access info: " + event.getAccessInfo(), st.getTid());
						}
					}
					if (event.isWrite()) {
						for (ShadowThread su : varState.readSets.keySet()) {
							if (su != st) {
								SyncSet readSet = varState.readSets.get(su);
								readSet.syncElements.putAll(varState.univSet.syncElements);
								if (!checkRaceFreedom(readSet, threadState, st, true)) {
									error(event, varState, "Read-write race", "Unknown prev op", -1, "Access info: " + event.getAccessInfo(), st.getTid());
								}
							}
						}
					}
				}

				// Update locksets
				if (event.isWrite()) {
					varState.readSets.clear();
					varState.writeSet.clear();
					varState.univSet.resetRetainingHeldLocks(threadState, st.getInnermostLock(), st, event.getAccessInfo().getLoc());
				} else {
					// Don't add to read set if univ set contains needed element(s).
					// TODO: a general problem is whether read set should be empty vs. not even mapped in readSets depending on whether current access is covered by univSet
					SyncSet readSet = varState.readSets.get(st);
					if (readSet == null) {
						readSet = SyncSet.makeSyncSet(varState.univSet.hasSyncElement(threadState) ? null : threadState,
						                              (st.getInnermostLock() == null || varState.univSet.hasSyncElement(getLockState(st.getInnermostLock()))) ? null : st.getInnermostLock(),
						                              st, event.getAccessInfo().getLoc());
						if (readSet != null) {
							varState.readSets.put(st, readSet);
						}
					} else {
						readSet.resetRetainingHeldLocks(varState.univSet.hasSyncElement(threadState) ? null : threadState,
						                                (st.getInnermostLock() == null || varState.univSet.hasSyncElement(getLockState(st.getInnermostLock()))) ? null : st.getInnermostLock(),
						                                st, event.getAccessInfo().getLoc());
					}
				}
				*/
			}

			if (COUNT_OPERATIONS) (event.isWrite() ? write : read).inc(st.getTid());

		} else {
			super.access(event);
		}
	}

	private boolean checkRaceFreedom(SyncSet syncSet, GoldilocksThreadState threadState, ShadowThread st, boolean doLazyTransfer) {
		if (COUNT_OPERATIONS) {
			syncSetCheckSizeHistogram[Math.min(syncSet.syncElements.size(), syncSetCheckSizeHistogram.length - 1)].inc(st.getTid());
		}
		if (syncSet.hasHeldLock(st)) { // will mark provenance as used
			return true;
		}
		if (syncSet.hasSyncElement(threadState)) {
			syncSet.getProvenanceForSyncElement(threadState).markUsed();
			return true;
		}
		if (doLazyTransfer) {
			return syncSet.performTransfer(threadState); // will mark provenance as used if it returns true
		}
		return false;
	}

	// Counters for relative frequencies of each rule
	private static final ThreadLocalCounter read = new ThreadLocalCounter("Goldilocks", "Read", RR.maxTidOption.get());
	private static final ThreadLocalCounter write = new ThreadLocalCounter("Goldilocks", "Write", RR.maxTidOption.get());
	private static final ThreadLocalCounter acquire = new ThreadLocalCounter("Goldilocks", "Acquire", RR.maxTidOption.get());
	private static final ThreadLocalCounter release = new ThreadLocalCounter("Goldilocks", "Release", RR.maxTidOption.get());
	private static final ThreadLocalCounter fork = new ThreadLocalCounter("Goldilocks", "Fork", RR.maxTidOption.get());
	private static final ThreadLocalCounter join = new ThreadLocalCounter("Goldilocks", "Join", RR.maxTidOption.get());
	private static final ThreadLocalCounter barrier = new ThreadLocalCounter("Goldilocks", "Barrier", RR.maxTidOption.get());
	private static final ThreadLocalCounter wait = new ThreadLocalCounter("Goldilocks", "Wait", RR.maxTidOption.get());
	private static final ThreadLocalCounter vol = new ThreadLocalCounter("Goldilocks", "Volatile", RR.maxTidOption.get());

	static final ThreadLocalCounter syncSetElementsAddedByAccesses = new ThreadLocalCounter("Goldilocks", "SyncSet elements added by accesses", RR.maxTidOption.get());
	static final ThreadLocalCounter syncSetElementsAddedByLazyTransfer = new ThreadLocalCounter("Goldilocks", "SyncSet elements added by lazy transfer", RR.maxTidOption.get());
	static final ThreadLocalCounter[] syncSetCheckSizeHistogram = new ThreadLocalCounter[10];
	
	private static final ThreadLocalCounter other = new ThreadLocalCounter("Goldilocks", "Other", RR.maxTidOption.get());

	static {
		for (int i = 0; i < syncSetCheckSizeHistogram.length; i++) {
			 syncSetCheckSizeHistogram[i] = new ThreadLocalCounter("Goldilocks", "Race checks on SyncSet of size " + i + (i == syncSetCheckSizeHistogram.length - 1 ? "+" : ""), RR.maxTidOption.get());
		}
		
		AggregateCounter reads = new AggregateCounter("Goldilocks", "Total Reads", read);
		AggregateCounter writes = new AggregateCounter("Goldilocks", "Total Writes", write);
		AggregateCounter accesses = new AggregateCounter("Goldilocks", "Total Access Ops", reads, writes);
		/*AggregateCounter syncSetElementsAdded = */ new AggregateCounter("Goldilocks", "Total SyncSet elements added", syncSetElementsAddedByAccesses, syncSetElementsAddedByLazyTransfer);
		/*AggregateCounter raceChecks = */ new AggregateCounter("Goldilocks", "Total race checks", syncSetCheckSizeHistogram);
		new AggregateCounter("Goldilocks", "Total Ops", accesses, acquire, release, fork, join, barrier, wait, vol, other);
	}


	@Override
	public void volatileAccess(final VolatileAccessEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksVolatileState volatileState = getVolatileState(event.getShadowVolatile());

		// TODO: is synchronization needed here?
		
		if (event.isWrite()) {
			// Transfer T to v for all vars
			Transfer.addTransfer(threadState, volatileState, event.getAccessInfo().getLoc());
		} else {
			// Transfer v to T for all vars
			Transfer.addTransfer(volatileState, threadState, event.getAccessInfo().getLoc());
		}

		super.volatileAccess(event);
		if (COUNT_OPERATIONS) vol.inc(st.getTid());
	}


	// st forked su
	@Override
	public void preStart(final StartEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState myThreadState = ts_get_threadState(st);
		final ShadowThread su = event.getNewThread();
		final GoldilocksThreadState otherThreadState = ts_get_threadState(su);

		// Transfer T to U for all vars
		Transfer.addTransfer(myThreadState, otherThreadState, event.getInfo().getLoc());
		
		super.preStart(event);
		if (COUNT_OPERATIONS) fork.inc(st.getTid());
	}


	@Override
	public void stop(ShadowThread st) {
		super.stop(st);
		if (COUNT_OPERATIONS) other.inc(st.getTid());
	}

	// t joined on u
	@Override
	public void postJoin(final JoinEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState myThreadState = ts_get_threadState(st);
		final ShadowThread su = event.getJoiningThread();
		final GoldilocksThreadState otherThreadState = ts_get_threadState(su);

		// Transfer U to T for all vars
		Transfer.addTransfer(otherThreadState, myThreadState, event.getInfo().getLoc());

		super.postJoin(event);	
		if (COUNT_OPERATIONS) join.inc(st.getTid());
	}


	@Override
	public void preWait(WaitEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksLockState lockState = getLockState(event.getLock());

		// Transfer T to m for all vars
		Transfer.addTransfer(threadState, lockState, event.getInfo().getLoc());
		
		super.preWait(event);
		if (COUNT_OPERATIONS) wait.inc(st.getTid());
	}

	@Override
	public void postWait(WaitEvent event) { 
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksLockState lockState = getLockState(event.getLock());
		
		// Transfer m to T for all vars
		Transfer.addTransfer(lockState, threadState, event.getInfo().getLoc());
		
		super.postWait(event);
		if (COUNT_OPERATIONS) wait.inc(st.getTid());
	}

	public static String toString(final ShadowThread td) {
		return String.format("[tid=%-2d]", td.getTid());
	}


	/*private final Decoration<ShadowThread, VectorClock> vectorClockForBarrierEntry = 
			ShadowThread.makeDecoration("FT:barrier", DecorationFactory.Type.MULTIPLE, new NullDefault<ShadowThread, VectorClock>());*/

	public void preDoBarrier(BarrierEvent<Object> event) {
		Assert.fail("Unsupported");
		final ShadowThread st = event.getThread();
		/*
		final FTBarrierState barrierObj = event.getBarrier();
		synchronized(barrierObj) {
			final VectorClock barrierV = barrierObj.enterBarrier();
			barrierV.max(ts_get_V(st));
			vectorClockForBarrierEntry.set(st, barrierV);
		}
		*/
		if (COUNT_OPERATIONS) barrier.inc(st.getTid());
	}

	public void postDoBarrier(BarrierEvent<Object> event) {
		Assert.fail("Unsupported");
		final ShadowThread st = event.getThread();
		/*
		final FTBarrierState barrierObj = event.getBarrier();
		synchronized(barrierObj) {
			final VectorClock barrierV = vectorClockForBarrierEntry.get(st);
			barrierObj.stopUsingOldVectorClock(barrierV);
			maxAndIncEpochAndCV(st, barrierV, null);
		}
		*/
		if (COUNT_OPERATIONS) barrier.inc(st.getTid());
	}

	///

	@Override
	public void classInitialized(ClassInitializedEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksVolatileState volatileState;
		
		synchronized (classInitTime) {
			volatileState = classInitTime.get(event.getRRClass());
		}

		// Transfer T to v for all vars
		Transfer.addTransfer(threadState, volatileState, event.getRRClass().getLoc());

		super.classInitialized(event);
		if (COUNT_OPERATIONS) other.inc(st.getTid());
	}

	@Override
	public void classAccessed(ClassAccessedEvent event) {
		final ShadowThread st = event.getThread();
		final GoldilocksThreadState threadState = ts_get_threadState(st);
		final GoldilocksVolatileState volatileState;

		// TODO: it probably isn't right to use the same site for both classInitialized (here) and classAccessed (below)

		synchronized (classInitTime) {
			volatileState = classInitTime.get(event.getRRClass());
		}

		// Transfer v to T for all vars
		Transfer.addTransfer(volatileState, threadState, event.getRRClass().getLoc());

		if (COUNT_OPERATIONS) other.inc(st.getTid());
	}



	@Override
	public void printXML(XMLWriter xml) {
		for (ShadowThread td : ShadowThread.getThreads()) {
			xml.print("thread", toString(td));
		}
	}


	protected void error(final AccessEvent ae, final GoldilocksVarState x, final String description, final String prevOp, final int prevTid, final String curOp, final int curTid) {

		if (ae instanceof FieldAccessEvent) {
			fieldError((FieldAccessEvent)ae, x, description, prevOp, prevTid, curOp, curTid);
		} else {
			arrayError((ArrayAccessEvent)ae, x, description, prevOp, prevTid, curOp, curTid);
		}
	}

	protected void arrayError(final ArrayAccessEvent aae, final GoldilocksVarState sx, final String description, final String prevOp, final int prevTid, final String curOp, final int curTid) {
		final ShadowThread st = aae.getThread();
		final Object target = aae.getTarget();

		if (arrayErrors.stillLooking(aae.getInfo())) {
			arrayErrors.error(st,
					aae.getInfo(),
					"Alloc Site", 		ArrayAllocSiteTracker.get(target),
					"Shadow State", 	sx,
					"Current Thread",	toString(st), 
					"Array",			Util.objectToIdentityString(target) + "[" + aae.getIndex() + "]",
					"Message", 			description,
					"Previous Op",		prevOp + (prevTid >= 0 ? " " + ShadowThread.get(prevTid) : ""),
					"Currrent Op",		curOp + " " + ShadowThread.get(curTid), 
					"Stack",			ShadowThread.stackDumpForErrorMessage(st)); 
		}
		Assert.assertTrue(prevTid != curTid);

		aae.getArrayState().specialize();

		if (!arrayErrors.stillLooking(aae.getInfo())) {
			advance(aae);
		}
	}

	protected void fieldError(final FieldAccessEvent fae, final GoldilocksVarState sx, final String description, final String prevOp, final int prevTid, final String curOp, final int curTid) {
		final FieldInfo fd = fae.getInfo().getField();
		final ShadowThread st = fae.getThread();
		final Object target = fae.getTarget();

		if (fieldErrors.stillLooking(fd)) {
			fieldErrors.error(st,
					fd,
					"Shadow State", 	sx,
					"Current Thread",	toString(st), 
					"Class",			(target==null?fd.getOwner():target.getClass()),
					"Field",			Util.objectToIdentityString(target) + "." + fd, 
					"Message", 			description,
					"Previous Op",		prevOp + (prevTid >= 0 ? " " + ShadowThread.get(prevTid) : ""),
					"Currrent Op",		curOp + " " + ShadowThread.get(curTid), 
					"Stack",			ShadowThread.stackDumpForErrorMessage(st));
		}

		Assert.assertTrue(prevTid != curTid);

		if (!fieldErrors.stillLooking(fd)) {
			advance(fae);
		}
	}
}

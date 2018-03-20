/******************************************************************************

Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
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


package tools.wdc;


import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Stack;
import java.util.concurrent.Callable;

import rr.org.objectweb.asm.Opcodes;
import rr.annotations.Abbrev;
import rr.barrier.BarrierEvent;
import rr.barrier.BarrierListener;
import rr.barrier.BarrierMonitor;
import rr.error.ErrorMessage;
import rr.error.ErrorMessages;
import rr.event.AccessEvent;
import rr.event.AcquireEvent;
import rr.event.ArrayAccessEvent;
import rr.event.ClassAccessedEvent;
import rr.event.ClassInitializedEvent;
import rr.event.Event;
import rr.event.FieldAccessEvent;
import rr.event.JoinEvent;
import rr.event.MethodEvent;
import rr.event.NewThreadEvent;
import rr.event.NotifyEvent;
import rr.event.ReleaseEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.event.WaitEvent;
import rr.event.AccessEvent.Kind;
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
import acme.util.Yikes;
import acme.util.count.ThreadLocalCounter;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DefaultValue;
import acme.util.decorations.NullDefault;
import acme.util.identityhash.WeakIdentityHashMap;
import acme.util.decorations.DecorationFactory.Type;
import acme.util.io.XMLWriter;
import acme.util.option.CommandLine;

@Abbrev("WDC")
public class WDCTool extends Tool implements BarrierListener<WDCBarrierState>, Opcodes {

	static final int INIT_CV_SIZE = 4;
	private static final ThreadLocalCounter total = new ThreadLocalCounter("DC", "Total Events", RR.maxTidOption.get());
	private static final ThreadLocalCounter exit = new ThreadLocalCounter("DC", "Exit", RR.maxTidOption.get());
	private static final ThreadLocalCounter fake_fork = new ThreadLocalCounter("DC", "Fake Fork", RR.maxTidOption.get());
	private static final ThreadLocalCounter acquire = new ThreadLocalCounter("DC", "Acquire", RR.maxTidOption.get());
	private static final ThreadLocalCounter release = new ThreadLocalCounter("DC", "Release", RR.maxTidOption.get());
	private static final ThreadLocalCounter write = new ThreadLocalCounter("DC", "Write", RR.maxTidOption.get());
	private static final ThreadLocalCounter read = new ThreadLocalCounter("DC", "Read", RR.maxTidOption.get());
	private static final ThreadLocalCounter writeFP = new ThreadLocalCounter("DC", "WriteFastPath", RR.maxTidOption.get());
	private static final ThreadLocalCounter readFP = new ThreadLocalCounter("DC", "ReadFastPath", RR.maxTidOption.get());
	private static final ThreadLocalCounter volatile_write = new ThreadLocalCounter("DC", "Volatile Write", RR.maxTidOption.get());
	private static final ThreadLocalCounter volatile_read = new ThreadLocalCounter("DC", "Volatile Read", RR.maxTidOption.get());
	private static final ThreadLocalCounter start = new ThreadLocalCounter("DC", "Start", RR.maxTidOption.get());
	private static final ThreadLocalCounter join = new ThreadLocalCounter("DC", "Join", RR.maxTidOption.get());
	private static final ThreadLocalCounter preWait = new ThreadLocalCounter("DC", "Pre Wait", RR.maxTidOption.get());
	private static final ThreadLocalCounter postWait = new ThreadLocalCounter("DC", "Post Wait", RR.maxTidOption.get());
	private static final ThreadLocalCounter class_init = new ThreadLocalCounter("DC", "Class Initialized", RR.maxTidOption.get());
	private static final ThreadLocalCounter class_accessed = new ThreadLocalCounter("DC", "Class Accessed", RR.maxTidOption.get());
	private static final ThreadLocalCounter example = new ThreadLocalCounter("DC", "Total Example Events", RR.maxTidOption.get());
	static final Object event_lock = new Object();
	public final ErrorMessage<FieldInfo> fieldErrors = ErrorMessages.makeFieldErrorMessage("WDC");
	public final ErrorMessage<ArrayAccessInfo> arrayErrors = ErrorMessages.makeArrayErrorMessage("WDC");

	static boolean BUILD_EVENT_GRAPH;
	static boolean PRINT_EVENT;
	static boolean COUNT_EVENT = true;
	static boolean TESTING_CONFIG;
	static boolean HB_WCP_ONLY;
	static boolean CAPO_CONFIG;
	static boolean HB_ONLY;
	static final boolean VERBOSE = false;
	
	// Can use the same data for class initialization synchronization as for volatiles
	public static final Decoration<ClassInfo,WDCVolatileData> classInitTime = MetaDataInfoMaps.getClasses().makeDecoration("WDC:InitTime", Type.MULTIPLE, 
			new DefaultValue<ClassInfo,WDCVolatileData>() {
		public WDCVolatileData get(ClassInfo t) {
			return new WDCVolatileData(null);
		}
	});

	public WDCTool(final String name, final Tool next, CommandLine commandLine) {
		super(name, next, commandLine);
		new BarrierMonitor<WDCBarrierState>(this, new DefaultValue<Object,WDCBarrierState>() {
			public WDCBarrierState get(Object k) {
				return new WDCBarrierState(ShadowLock.get(k));
			}
		});
	}

	static CV ts_get_hb(ShadowThread ts) { Assert.panic("Bad");	return null; }
	static void ts_set_hb(ShadowThread ts, CV cv) { Assert.panic("Bad");  }

	static CV ts_get_wcp(ShadowThread ts) { Assert.panic("Bad");	return null; }
	static void ts_set_wcp(ShadowThread ts, CV cv) { Assert.panic("Bad");  }

	static CV ts_get_wdc(ShadowThread ts) { Assert.panic("Bad");	return null; }
	static void ts_set_wdc(ShadowThread ts, CV cv) { Assert.panic("Bad");  }
	
	// We only maintain the "last event" if BUILD_EVENT_GRAPH == true
	static EventNode ts_get_lastEventNode(ShadowThread ts) { Assert.panic("Bad"); return null; }
	static void ts_set_lastEventNode(ShadowThread ts, EventNode eventNode) { Assert.panic("Bad"); }
	
	// Maintain the a stack of current held critical sections per thread
	static Stack<AcqRelNode> ts_get_holdingLocks(ShadowThread ts) { Assert.panic("Bad"); return null; }
	static void ts_set_holdingLocks(ShadowThread ts, Stack<AcqRelNode> heldLocks) { Assert.panic("Bad"); }

	static final Decoration<ShadowLock,WDCLockData> wdcLockData = ShadowLock.makeDecoration("WDC:ShadowLock", DecorationFactory.Type.MULTIPLE,
			new DefaultValue<ShadowLock,WDCLockData>() { public WDCLockData get(final ShadowLock ld) { return new WDCLockData(ld); }});

	static final WDCLockData get(final ShadowLock ld) {
		return wdcLockData.get(ld);
	}

	static final Decoration<ShadowVolatile,WDCVolatileData> wdcVolatileData = ShadowVolatile.makeDecoration("WDC:shadowVolatile", DecorationFactory.Type.MULTIPLE,
			new DefaultValue<ShadowVolatile,WDCVolatileData>() { public WDCVolatileData get(final ShadowVolatile ld) { return new WDCVolatileData(ld); }});

	static final WDCVolatileData get(final ShadowVolatile ld) {
		return wdcVolatileData.get(ld);
	}

	@Override
	final public ShadowVar makeShadowVar(final AccessEvent fae) {
		ShadowThread td = fae.getThread();
		int tid = td.getTid();
		CV hb = ts_get_hb(td);
		CV wcp = null;
		if (!HB_ONLY) wcp = ts_get_wcp(td);
		CV wdc = null;
		if (!HB_WCP_ONLY) wdc = ts_get_wdc(td);
		CV wcpUnionPO = null;
		if (!HB_ONLY) {
			wcpUnionPO = new CV(wcp);
			wcpUnionPO.set(tid, hb.get(tid));
		}
		//TODO: volatile is treated differently. Does this cause issues without global lock?
		if (fae.getKind() == Kind.VOLATILE) {
			WDCVolatileData vd = get(((VolatileAccessEvent)fae).getShadowVolatile());
			// Instead of the commented-out stuff, I think they should just be initialized to bottom
			/*
			vd.hb.max(hb);
			vd.wcp.max(wcpUnionPO); // use wcpUnionPO
			vd.wdc.max(wdc);
			*/
			return super.makeShadowVar(fae);
		} else {
			return new WDCGuardState(fae.isWrite(), hb, wcpUnionPO, wdc); // Send wcpUnionPO for the wcp parameter
		}
	}

	@Override
	public void create(NewThreadEvent e) {
		ShadowThread currentThread = e.getThread();
		synchronized(currentThread) { //Is this over kill?
		CV hb = ts_get_hb(currentThread);

		if (hb == null) {			
			hb = new CV(INIT_CV_SIZE);
			ts_set_hb(currentThread, hb);
			hb.inc(currentThread.getTid());

			if (!HB_ONLY) {
				CV wcp = new CV(INIT_CV_SIZE);
				ts_set_wcp(currentThread, wcp);
				//wcp.inc(currentThread.getTid()); // Don't increment WCP since it doesn't include PO
			}

			if (!HB_WCP_ONLY) {
				CV wdc = new CV(INIT_CV_SIZE);
				ts_set_wdc(currentThread, wdc);
				wdc.inc(currentThread.getTid());
			}
		}
		}
		super.create(e);

	}

//	@Override
//	public void get(ShadowThread futureTd, ShadowThread mainTd) {
//		synchronized(event_lock) {
//			Util.log("future td: " + futureTd);
//			Util.log("main td: " + mainTd);
//		}
//		super.get(futureTd, mainTd);
//	}
	
	@Override
	public void exit(MethodEvent me) {	
		ShadowThread td = me.getThread();
		//TODO: Excessive exits satisfy this condition since the class associated with the original call can not be found -- still true?
		if (td.getParent() == null && td.getTid() != 0 /*not the main thread*/ && !td.getThread().getName().equals("Finalizer")) {
			String methodName = me.getInfo().getName();
			Object target = me.getTarget();
			if ((methodName.equals("call") && target instanceof Callable) ||
			    (methodName.equals("run")  && target instanceof Runnable)) {
				//(me.getInfo().toSimpleName().contains(".call") || me.getInfo().toSimpleName().contains(".run"))) {
				synchronized(td) {
					if (COUNT_EVENT) total.inc(td.getTid());
					if (COUNT_EVENT) exit.inc(td.getTid());
					//Get the main thread
					//TODO: Main thread is being accessed by another thread
					ShadowThread main = ShadowThread.get(0);
					synchronized(main) { //Will this deadlock? Main should never be waiting on any other thread so I don't think so.
					if (VERBOSE) Assert.assertTrue(main != null);//, "The main thread can not be found.");

					EventNode dummyEventNode = null;
					EventNode thisEventNode = null;
					if (BUILD_EVENT_GRAPH) {
						//Build this thread's event node
						AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
						thisEventNode = new EventNode(-2, -1, td.getTid(), currentCriticalSection, "join [exit join]");
						handleEvent(me, thisEventNode);
						if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
						//Build dummy eventNode
						if (COUNT_EVENT) total.inc(td.getTid()); //TODO: should dummy event increment the event count?
						currentCriticalSection = getCurrentCriticalSection(main);
						dummyEventNode = new EventNode(ts_get_lastEventNode(main).eventNumber+1, -1, main.getTid(), currentCriticalSection, "exit [dummy event]");
						//PO last event node in main to dummy node
						EventNode priorMainNode = ts_get_lastEventNode(main);
						EventNode.addEdge(priorMainNode, dummyEventNode);
						ts_set_lastEventNode(main, dummyEventNode);
						//Create a hard edge from thisEventNode to dummy node
						EventNode.addEdge(thisEventNode, dummyEventNode);
					} else {
						//PO last event node in this thread to this thread's current event node
						//Set this event node to this thread's latest event node
						handleEvent(me, thisEventNode);
						if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
					}
					
					if (PRINT_EVENT) Util.log("exit "+me.getInfo().toSimpleName()+" by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));

					//Update main's vector clocks with that of the joining thread
					ts_get_hb(main).max(ts_get_hb(td));
					if (!HB_ONLY) ts_get_wcp(main).max(ts_get_hb(td));
					if (!HB_WCP_ONLY) ts_get_wdc(main).max(ts_get_wdc(td));
					}
				}
			}
		}
		super.exit(me);
	}

	@Override
	public void stop(ShadowThread td) {
		super.stop(td);
	}
	
	@Override
	public void init() {
		PRINT_EVENT = RR.printEventOption.get();
		TESTING_CONFIG = RR.testingConfigOption.get();
		HB_WCP_ONLY = RR.wdcHBWCPOnlyOption.get();
		CAPO_CONFIG = RR.CAPOOption.get();
		HB_ONLY = RR.wdcHBOnlyOption.get();
		
		if (HB_ONLY) HB_WCP_ONLY = true;
		//Disable event graph when testing config is turned on.
		//The constraint graph G can not be constructed if WDC is not tracked
		if (TESTING_CONFIG || HB_WCP_ONLY) {
			BUILD_EVENT_GRAPH = false;
		} else {
			BUILD_EVENT_GRAPH = RR.wdcBuildEventGraph.get();
		}
		
		if (BUILD_EVENT_GRAPH) {
			COUNT_EVENT = true;
		}

		Util.log("testing config: " + TESTING_CONFIG);
		Util.log("build config: " + BUILD_EVENT_GRAPH);
		Util.log("hb wcp only config: " + HB_WCP_ONLY);
		Util.log("hb only config: " + HB_ONLY);
		Util.log("rule b removed config: " + CAPO_CONFIG);
	}
	
	//TODO: Only a single thread should be accessing the list of races and the event graph
	@Override
	public void fini() {
		StaticRace.reportRaces();
		//With no constraint graph races can not be checked
		//If this is changed, HB_WCP_ONLY configuration should not check races since DC constraint graph is not tracked
		
		if (BUILD_EVENT_GRAPH) {
			// Store Reordered Traces
			File commandDir = storeReorderedTraces();
			
			// Races (based on an identifying string) that we've verified have no cycle.
			// Other dynamic instances of the same static race might have a cycle, but we've already demonstrated that this static race is predictable, so who cares?
			HashSet<StaticRace> verifiedRaces = new HashSet<StaticRace>();
			LinkedList<StaticRace> staticOnlyCheck = new LinkedList<StaticRace>();
			long start = System.currentTimeMillis();
			for (StaticRace wdcRace : StaticRace.wdcRaces) {
				// Only do WDC-B processing on WDC-races (not HB- or WCP-races)
				if (!wdcRace.raceType.isWCPRace() && 
						(StaticRace.staticRaceMap.get(RaceType.WCPRace) == null || (StaticRace.staticRaceMap.get(RaceType.WCPRace) != null && !StaticRace.staticRaceMap.get(RaceType.WCPRace).containsKey(wdcRace))) &&
						(StaticRace.staticRaceMap.get(RaceType.HBRace) == null || (StaticRace.staticRaceMap.get(RaceType.HBRace) != null && !StaticRace.staticRaceMap.get(RaceType.HBRace).containsKey(wdcRace)))) {
					vindicateRace(wdcRace, verifiedRaces, staticOnlyCheck, true, true, commandDir);
				}
			}
			Util.log("Static DC Race Check Time: " + (System.currentTimeMillis() - start));
			for (StaticRace singleStaticRace : staticOnlyCheck) {
				StaticRace.wdcRaces.remove(singleStaticRace);
			}
			//Dynamic DC-race only check
			start = System.currentTimeMillis();
			for (StaticRace wdcRace : StaticRace.wdcRaces) {
				// Only do WDC-B processing on WDC-races (not HB- or WCP-races)
				if (!wdcRace.raceType.isWCPRace()) {
					vindicateRace(wdcRace, verifiedRaces, staticOnlyCheck, false, true, commandDir);
				}
			}
			Util.log("Dynamic DC Race Check Time: " + (System.currentTimeMillis() - start));
			//Dynamic WCP-race only check
			start = System.currentTimeMillis();
			for (StaticRace wcpRace : StaticRace.wdcRaces) {
				// Only VindicateRace on WCP-races (not HB- or WDC-races)
				if (wcpRace.raceType.isWCPRace() && !wcpRace.raceType.isHBRace()) {
					vindicateRace(wcpRace, verifiedRaces, staticOnlyCheck, false, true, commandDir);
				}
			}
			Util.log("Dynamic WCP Race Check Time: " + (System.currentTimeMillis() - start));
			//Dynamic HB-race only check
			start = System.currentTimeMillis();
			for (StaticRace hbRace : StaticRace.wdcRaces) {
				// Only VindicateRace on HB-races (not WCP- or WDC-races)
				if (hbRace.raceType.isHBRace()) {
					vindicateRace(hbRace, verifiedRaces, staticOnlyCheck, false, true, commandDir);
				}
			}
			Util.log("Dynamic HB Race Check Time: " + (System.currentTimeMillis() - start));
//			Util.log("Printing HB/WCP Races.");
//			for (StaticRace wcpRace : StaticRace.wdcRaces) {
//				if (wcpRace.raceType.isWCPRace() && wcpRace.raceType != RaceType.WDCRace) {
//					vindicateRace(wcpRace, verifiedRaces, staticOnlyCheck, false, false, commandDir);
//				}
//			}
		}
	}
	
	public void vindicateRace(StaticRace DCrace, HashSet<StaticRace> verifiedRaces, LinkedList<StaticRace> staticOnlyCheck, boolean staticDCRacesOnly, boolean vindicateRace, File commandDir) {
		RdWrNode startNode = DCrace.firstNode;
		RdWrNode endNode = DCrace.secondNode;
		String desc = DCrace.raceType + " " + DCrace.description();
		// TODO: Mike asks: do we really need to remove this edge? i doubt that's necessary; it's a reasonable constraint on tr'
		// Jake says: I thought there was an example that added an cycle if there was a direct edge between conflicting accesses.
		// If this is true than the edge would be sufficient for the initial set of edges. So lines 2-7 of algorithm 3 in the paper would be unnecessary.
		if (vindicateRace) {
			// Remove edge between conflicting accesses of current race
			if (RR.wdcRemoveRaceEdge.get()) {
				// Remove edge between conflicting accesses of current race
				Util.println("removing conflicting access edge");
				EventNode.removeEdge(startNode, endNode);
			} else {
				Util.println("NOT removing conflicting access edge");
			}
			if ((staticDCRacesOnly && !verifiedRaces.contains(DCrace)) || !staticDCRacesOnly) {
				Util.println("Checking " + desc + " for event pair " + startNode + " -> " + endNode + " | " + startNode.getExampleNumber() + ", " + endNode.getExampleNumber() + " | distance: " + (endNode.eventNumber - startNode.eventNumber));
				Util.println("Next trying with traverseFromAllEdges = true and precision = true");
				boolean detectedCycle = EventNode.crazyNewEdges(startNode, endNode, true, true, commandDir);
				if (staticDCRacesOnly) {
					if (!detectedCycle) {
						verifiedRaces.add(DCrace);
					} else if (CAPO_CONFIG){
						boolean checkDCOrder = EventNode.addRuleB(startNode, endNode, true, true, commandDir);
						Util.println("Race pair " + desc + " is " + checkDCOrder + " DC ordered.");
					}
					staticOnlyCheck.add(DCrace);
				}
			}
			//Add edge between conflicting accesses of current race back to WDC-B graph
			if (RR.wdcRemoveRaceEdge.get()) {
				// If it was removed, add edge between conflicting accesses of current race back to WDC-B graph
				EventNode.addEdge(startNode, endNode);
			}
		} else {
			Util.println("Checking " + desc + " for event pair " + startNode + " -> " + endNode + " | " + startNode.getExampleNumber() + ", " + endNode.getExampleNumber() + " | distance: " + (endNode.eventNumber - startNode.eventNumber));
		}
	}
	
	//Tid -> Stack of ARNode
	AcqRelNode getCurrentCriticalSection(ShadowThread td) {
		Stack<AcqRelNode> locksHeld = ts_get_holdingLocks(td);
		if (locksHeld == null) {
			locksHeld = new Stack<AcqRelNode>();
			ts_set_holdingLocks(td, locksHeld);
		}
		AcqRelNode currentCriticalSection = locksHeld.isEmpty() ? null : locksHeld.peek();
		return currentCriticalSection;
	}
	
	void updateCurrentCriticalSectionAtAcquire(ShadowThread td, AcqRelNode acqNode) {
		Stack<AcqRelNode> locksHeld = ts_get_holdingLocks(td);
		locksHeld.push(acqNode);
	}
	
	AcqRelNode updateCurrentCriticalSectionAtRelease(ShadowThread td, AcqRelNode relNode) {
		Stack<AcqRelNode> locksHeld = ts_get_holdingLocks(td);
		AcqRelNode poppedNode = locksHeld.pop();
		return poppedNode;
	}
	
	void handleEvent(Event e, EventNode thisEventNode) {
		ShadowThread td = e.getThread();
		
		// Evaluating total.getCount() frequently will lead to lots of cache conflicts, so doing this local check instead.
		if (total.getLocal(td.getTid()) % 1000000 == 1) {
		//This is a rough estimate now since total.getCount() is not thread safe
		//if (total.getCount() % 1000000 == 1) {
			Util.println("Handling event " + total.getCount());
			/*
			if (event_count > 5000000) {
				fini();
				System.exit(0);
			}
			*/
		}
        
		if (BUILD_EVENT_GRAPH) {
			EventNode priorPOEventNode = ts_get_lastEventNode(td);
			if (priorPOEventNode == null) {
				// This is the first event of the thread
				if (VERBOSE) Assert.assertTrue((td.getParent() != null) == (ts_get_wdc(td) instanceof CVE));
				if (td.getParent() != null) {
					EventNode forkEventNode = ((CVE)ts_get_wdc(td)).eventNode;
					EventNode.addEdge(forkEventNode, thisEventNode);
				} else {
					if (td.getTid() != 0 //&& thisEventNode.eventNumber != 1 /*Every event after the first one on T0 will be PO ordered*/ 
							&& !td.getThread().getName().equals("Finalizer")) {
						if (PRINT_EVENT) Util.log("parentless fork to T"+td.getTid());
						if (COUNT_EVENT) fake_fork.inc(td.getTid());
						//Get the main thread
						//TODO: Same as exit, the main thread is being accessed by a different thread
						ShadowThread main = ShadowThread.get(0);
						synchronized(main) { //Will this deadlock? Same as exit, I don't think main will lock on this thread.
							if (VERBOSE) Assert.assertTrue(main != null);//, "The main thread can not be found.");
	
							//Create a hard edge from the last event in the main thread to the parentless first event in this thread
							int mainTid = main.getTid();
							final CV mainHB = ts_get_hb(main);
							final CV mainWCP = ts_get_wcp(main);
							final CV mainWDC = ts_get_wdc(main);
							final CV tdHB = ts_get_hb(td);
							final CV tdWCP = ts_get_wcp(td);
							CV tdWDC = ts_get_wdc(td);
							// Compute WCP before modifying tdHB
							//This needs to happen even if no build event graph
							tdWCP.max(mainHB); // Use HB here because this is a hard WCP edge
							tdHB.max(mainHB);
							mainHB.inc(mainTid);
							tdWDC.max(mainWDC);
							Assert.assertTrue(td.getTid() != 0);
							Assert.assertTrue(ts_get_lastEventNode(main) != null);
							ts_set_wdc(td, new CVE(tdWDC, ts_get_lastEventNode(main))); // for generating event node graph
							mainWDC.inc(mainTid);
							
							//Add edge from main to first event in the thread
							if (VERBOSE) Assert.assertTrue(((CVE)ts_get_wdc(td)).eventNode == ts_get_lastEventNode(main));
							
							EventNode forkEventNode = ((CVE)ts_get_wdc(td)).eventNode;
							EventNode.addEdge(forkEventNode, thisEventNode);
							
							if (VERBOSE && EventNode.VERBOSE_GRAPH) {
								EventNode eventOne = EventNode.threadToFirstEventMap.get(0);
								eventOne = EventNode.threadToFirstEventMap.get(0);
								Assert.assertTrue(eventOne.eventNumber == 1);//, "eventOne.eventNumber: " + eventOne.eventNumber);
								Assert.assertTrue(EventNode.bfsTraversal(forkEventNode, eventOne, null, Long.MIN_VALUE, Long.MAX_VALUE, true));//, "main T" + main.getTid() + " does not reach event 1. What: " + eventOne.getNodeLabel());
								Assert.assertTrue(EventNode.bfsTraversal(thisEventNode, eventOne, null, Long.MIN_VALUE, Long.MAX_VALUE, true));//, "Thread T" + td.getTid() + " does not reach event 1.");
							}
						}
					} else {
						// If the current event is the first event, give it an eventNumber of 1
						if (td.getTid() == 0 && thisEventNode.eventNumber < 0) {
							if (VERBOSE) Assert.assertTrue(thisEventNode.threadID == 0);
							thisEventNode.eventNumber = 1;
							if (EventNode.VERBOSE_GRAPH) EventNode.addEventToThreadToItsFirstEventsMap(thisEventNode);
						}
						if (VERBOSE) Assert.assertTrue(thisEventNode.eventNumber == 1 || td.getThread().getName().equals("Finalizer"));//, "Event Number: " + thisEventNode.eventNumber + " | Thread Name: " + td.getThread().getName());
					}
				}
			} else {
				EventNode.addEdge(priorPOEventNode, thisEventNode);
			}
			ts_set_lastEventNode(td, thisEventNode);
		} else if (td.getParent() == null && td.getTid() != 0 /*not main thread*/ && !td.getThread().getName().equals("Finalizer")) {
			final CV tdHB = ts_get_hb(td);
			if (tdHB.get(0) == 0) { //If this is the first event in the parentless thread
				if (PRINT_EVENT) Util.log("parentless fork to T"+td.getTid());
				if (COUNT_EVENT) fake_fork.inc(td.getTid());
				//Get the main thread
				//TODO: Same as exit, the main thread is being accessed by a different thread
				ShadowThread main = ShadowThread.get(0);
				synchronized(main) { //Will this deadlock? Same as exit, I don't think main will lock on this thread.
					if (VERBOSE) Assert.assertTrue(main != null);//, "The main thread can not be found.");
		
					//Create a hard edge from the last event in the main thread to the parentless first event in this thread
					int mainTid = main.getTid();
					final CV mainHB = ts_get_hb(main);
					CV mainWDC = null;
					if (!HB_WCP_ONLY) mainWDC = ts_get_wdc(main);
//					final CV tdHB = ts_get_hb(td);
					CV tdWCP = null;
					if (!HB_ONLY) tdWCP = ts_get_wcp(td);
					CV tdWDC = null;
					if (!HB_WCP_ONLY) tdWDC = ts_get_wdc(td);
					// Compute WCP before modifying tdHB
					if (!HB_ONLY) tdWCP.max(mainHB); // Use HB here because this is a hard WCP edge
					tdHB.max(mainHB);
					mainHB.inc(mainTid);
					if (!HB_WCP_ONLY) {
						tdWDC.max(mainWDC);
						ts_set_wdc(td, new CVE(tdWDC, ts_get_lastEventNode(main))); // for generating event node graph
						mainWDC.inc(mainTid);
					}
				}
			}
		}
	}
	
	@Override
	public void acquire(final AcquireEvent ae) {
		final ShadowThread td = ae.getThread();
		synchronized(td) {
			final ShadowLock shadowLock = ae.getLock();
			
			if (COUNT_EVENT) {
				//Update has to be done here so that the event nodes have correct partial ordering. 
				//The idea is to use lamport timestamps to create a partial order.
				total.inc(td.getTid()); 
			}
			if (COUNT_EVENT) acquire.inc(td.getTid());
			if (COUNT_EVENT) example.inc(td.getTid());
	
			//TODO: Anything build event graph 
			AcqRelNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) {
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				if (currentCriticalSection != null && VERBOSE) Assert.assertTrue(currentCriticalSection.shadowLock != shadowLock);
				thisEventNode = new AcqRelNode(-2/*ts_get_lastEventNode(td).eventNumber+1*/, -1, shadowLock, td.getTid(), true, currentCriticalSection);
				updateCurrentCriticalSectionAtAcquire(td, thisEventNode);
			}
			handleEvent(ae, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
			
			handleAcquire(td, shadowLock, thisEventNode, false);
			
			if (PRINT_EVENT) Util.log("acq("+Util.objectToIdentityString(shadowLock.getLock())+") by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
		}

		super.acquire(ae);
	}

	void handleAcquire(ShadowThread td, ShadowLock shadowLock, AcqRelNode thisEventNode, boolean isHardEdge) {
		//TODO: The lockData is protected by the lock corresponding to the shadowLock being currently held
		final WDCLockData lockData = get(shadowLock);
		
		if (BUILD_EVENT_GRAPH) {
			thisEventNode.eventNumber = lockData.latestRelNode == null ? thisEventNode.eventNumber : Math.max(thisEventNode.eventNumber, lockData.latestRelNode.eventNumber+1);
		}
		
		// HB
		CV hb = ts_get_hb(td);
		hb.max(lockData.hb);
		
		// WCP and WDC
		if (VERBOSE) Assert.assertTrue(lockData.readVars.isEmpty() && lockData.writeVars.isEmpty());
		CV wcp = null;
		if (!HB_ONLY) wcp = ts_get_wcp(td);
		CV wdc = null;
		if (!HB_WCP_ONLY) wdc = ts_get_wdc(td);
		// If a hard edge, union WCP with HB
		if (isHardEdge) {
			if (!HB_ONLY) wcp.max(lockData.hb);
			if (!HB_WCP_ONLY) wdc.max(lockData.wdc);
		} else {
			if (!HB_ONLY) wcp.max(lockData.wcp);
			// wdc.max(lockData.wdc); // Don't do this for WDC, since it's not transitive with HB
		}
		int tid = td.getTid();
		CV wcpUnionPO = null;
		if (!HB_ONLY) {
			wcpUnionPO = new CV(wcp);
			wcpUnionPO.set(tid, hb.get(tid));
		}
		CVE wdcCVE = null;
		if (!HB_WCP_ONLY) wdcCVE = new CVE(wdc, thisEventNode);
		//TODO: The acqQueueMap is protected by the lock corresponding to the shadowLock being currently held
		for (ShadowThread otherTD : ShadowThread.getThreads()) {
			if (otherTD != td) {
				// WCP
				if (!HB_ONLY) {
					ArrayDeque<CV> queue = lockData.wcpAcqQueueMap.get(otherTD);
					if (queue == null) {
						queue = lockData.wcpAcqQueueGlobal.clone(); // Include any stuff that didn't get added because otherTD hadn't been created yet
						lockData.wcpAcqQueueMap.put(otherTD, queue);
					}
					queue.addLast(wcpUnionPO);
				}
				// WDC
				if (!HB_WCP_ONLY && !CAPO_CONFIG) {
					PerThreadQueue<CVE> ptQueue = lockData.wdcAcqQueueMap.get(otherTD);
					if (ptQueue == null) {
						ptQueue = lockData.wdcAcqQueueGlobal.clone();
						lockData.wdcAcqQueueMap.put(otherTD, ptQueue);
					}
					ptQueue.addLast(td, wdcCVE);
				}
			}
		}
		// Also add to the queue that we'll use for any threads that haven't been created yet.
		// But before doing that, be sure to initialize *this thread's* queues for the lock using the global queues.
		if (!HB_ONLY) {
			ArrayDeque<CV> acqQueue = lockData.wcpAcqQueueMap.get(td);
			if (acqQueue == null) {
				acqQueue = lockData.wcpAcqQueueGlobal.clone();
				lockData.wcpAcqQueueMap.put(td, acqQueue);
			}
			ArrayDeque<CV> relQueue = lockData.wcpRelQueueMap.get(td);
			if (relQueue == null) {
				relQueue = lockData.wcpRelQueueGlobal.clone();
				lockData.wcpRelQueueMap.put(td, relQueue);
			}
			lockData.wcpAcqQueueGlobal.addLast(wcpUnionPO);
		}
		// Same for WDC
		if (!HB_WCP_ONLY && !CAPO_CONFIG) {
			PerThreadQueue<CVE> acqPTQueue = lockData.wdcAcqQueueMap.get(td);
			if (acqPTQueue == null) {
				acqPTQueue = lockData.wdcAcqQueueGlobal.clone();
				lockData.wdcAcqQueueMap.put(td, acqPTQueue);
			}
			PerThreadQueue<CVE> relPTQueue = lockData.wdcRelQueueMap.get(td);
			if (relPTQueue == null) {
				relPTQueue = lockData.wdcRelQueueGlobal.clone();
				lockData.wdcRelQueueMap.put(td, relPTQueue);
			}
			lockData.wdcAcqQueueGlobal.addLast(td, wdcCVE);
		}
	}

	@Override
	public void release(final ReleaseEvent re) {
		final ShadowThread td = re.getThread();
		synchronized(td) {
			final ShadowLock shadowLock = re.getLock();
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) release.inc(td.getTid());
			if (COUNT_EVENT) example.inc(td.getTid());

			AcqRelNode thisEventNode = null;
			AcqRelNode matchingAcqNode = null;
			if (BUILD_EVENT_GRAPH) {
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				if (VERBOSE) Assert.assertTrue(currentCriticalSection != null);
				thisEventNode = new AcqRelNode(-2, -1, shadowLock, td.getTid(), false, currentCriticalSection);
				matchingAcqNode = updateCurrentCriticalSectionAtRelease(td, thisEventNode);
			}
			handleEvent(re, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
			
			if (PRINT_EVENT) Util.log("rel("+Util.objectToIdentityString(shadowLock.getLock())+") by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));

			handleRelease(td, shadowLock, thisEventNode);
			if (BUILD_EVENT_GRAPH) {
				if (VERBOSE) Assert.assertTrue(matchingAcqNode == thisEventNode.otherCriticalSectionNode);
			}
		}

		super.release(re);

	}
	
	void handleRelease(ShadowThread td, ShadowLock shadowLock, AcqRelNode thisEventNode) {
		final WDCLockData lockData = get(shadowLock);

		CV hb = ts_get_hb(td);
		CV wcp = null;
		if (!HB_ONLY) wcp = ts_get_wcp(td);
		CV wdc = null;
		if (!HB_WCP_ONLY) wdc = ts_get_wdc(td);

		int tid = td.getTid();
		CV wcpUnionPO = null;
		if (!HB_ONLY) {
			wcpUnionPO = new CV(wcp);
			wcpUnionPO.set(tid, hb.get(tid));
			// WCP: process queue elements
			ArrayDeque<CV> acqQueue = lockData.wcpAcqQueueMap.get(td);
			ArrayDeque<CV> relQueue = lockData.wcpRelQueueMap.get(td);
			while (!acqQueue.isEmpty() && !acqQueue.peekFirst().anyGt(wcpUnionPO)) {
				acqQueue.removeFirst();
				wcp.max(relQueue.removeFirst());
			}
		}
		
		if (BUILD_EVENT_GRAPH) {
			AcqRelNode myAcqNode = thisEventNode.surroundingCriticalSection;
//			AcqRelNode myAcqNode = (AcqRelNode)lockData.wdcAcqQueueGlobal.peekLast(td).eventNode;
			if (VERBOSE) Assert.assertTrue(myAcqNode.isAcquire());
			//TODO: this release's corresponding acquire node should not be touched while the lock is currently held
			thisEventNode.otherCriticalSectionNode = myAcqNode;
			myAcqNode.otherCriticalSectionNode = thisEventNode;
		}

		// WDC: process queue elements
		if (!HB_WCP_ONLY && !CAPO_CONFIG) {
			PerThreadQueue<CVE> acqPTQueue = lockData.wdcAcqQueueMap.get(td);
			if (VERBOSE) Assert.assertTrue(acqPTQueue.isEmpty(td));
			PerThreadQueue<CVE> relPTQueue = lockData.wdcRelQueueMap.get(td);
			for (ShadowThread otherTD : ShadowThread.getThreads()) {
				if (otherTD != td) {
					while (!acqPTQueue.isEmpty(otherTD) && !acqPTQueue.peekFirst(otherTD).anyGt(wdc)) {
						acqPTQueue.removeFirst(otherTD);
						CVE prevRel = relPTQueue.removeFirst(otherTD);
						
						if (BUILD_EVENT_GRAPH) {
							// If local VC is up-to-date w.r.t. prevRel, then no need to add a new edge
							//TODO: protected by the fact that the lock is currently held
							if (prevRel.anyGt(wdc)) {
								EventNode.addEdge(prevRel.eventNode, thisEventNode);
							}
						}
						
						wdc.max(prevRel);
					}
				}
			}
		}
		
		// WCP: rule (a)
		if (!HB_ONLY) {
			for (ShadowVar var : lockData.readVars) {
				CV cv = lockData.wcpReadMap.get(var);
				if (cv == null) {
					cv = new CV(WDCTool.INIT_CV_SIZE);
					lockData.wcpReadMap.put(var, cv);
				}
				cv.max(hb);
			}
			for (ShadowVar var : lockData.writeVars) {
				CV cv = lockData.wcpWriteMap.get(var);
				if (cv == null) {
					cv = new CV(WDCTool.INIT_CV_SIZE);
					lockData.wcpWriteMap.put(var, cv);
				}
				cv.max(hb);
			}
		}
		// WDC: rule (a)
		if (!HB_WCP_ONLY) {
			for (ShadowVar var : lockData.readVars) {
				CVE cv = lockData.wdcReadMap.get(var);
				if (cv == null) {
					cv = new CVE(new CV(WDCTool.INIT_CV_SIZE), thisEventNode);
					lockData.wdcReadMap.put(var, cv);
				} else {
					cv.setEventNode(thisEventNode);
				}
				cv.max(wdc);
			}
			for (ShadowVar var : lockData.writeVars) {
				CVE cv = lockData.wdcWriteMap.get(var);
				if (cv == null) {
					cv = new CVE(new CV(WDCTool.INIT_CV_SIZE), thisEventNode);
					lockData.wdcWriteMap.put(var, cv);
				} else {
					cv.setEventNode(thisEventNode);
				}
				cv.max(wdc);
			}
		}
		// Assign to lock
		lockData.hb.assignWithResize(hb);
		if (!HB_ONLY) lockData.wcp.assignWithResize(wcp);
		if (!HB_WCP_ONLY) lockData.wdc.assignWithResize(wdc); // Used for hard notify -> wait edge
		
		// WCP: add to release queues
		CV hbCopy = new CV(hb);
		if (!HB_ONLY) {
			for (ShadowThread otherTD : ShadowThread.getThreads()) {
				if (otherTD != td) {
					ArrayDeque<CV> queue = lockData.wcpRelQueueMap.get(otherTD);
					if (queue == null) {
						queue = lockData.wcpRelQueueGlobal.clone(); // Include any stuff that didn't get added because otherTD hadn't been created yet
						lockData.wcpRelQueueMap.put(otherTD, queue);
					}
					queue.addLast(hbCopy);
				}
			}
			// Also add to the queue that we'll use for any threads that haven't been created yet
			lockData.wcpRelQueueGlobal.addLast(hbCopy);
		}
		
		// WDC: add to release queues
		if (!HB_WCP_ONLY && !CAPO_CONFIG) {
			CVE wdcCVE = new CVE(wdc, thisEventNode);
			for (ShadowThread otherTD : ShadowThread.getThreads()) {
				if (otherTD != td) {
					PerThreadQueue<CVE> queue = lockData.wdcRelQueueMap.get(otherTD);
					if (queue == null) {
						queue = lockData.wdcRelQueueGlobal.clone(); // Include any stuff that didn't get added because otherTD hadn't been created yet
						lockData.wdcRelQueueMap.put(otherTD, queue);
					}
					queue.addLast(td, wdcCVE);
				}
			}
			// Also add to the queue that we'll use for any threads that haven't been created yet
			lockData.wdcRelQueueGlobal.addLast(td, wdcCVE);
		}

		// WCP and WDC: clear readVars and writeVars
		if (!HB_ONLY) {
			lockData.readVars = new HashSet<ShadowVar>();
			lockData.writeVars = new HashSet<ShadowVar>();
			lockData.wcpReadMap = getPotentiallyShrunkMap(lockData.wcpReadMap);
			lockData.wcpWriteMap = getPotentiallyShrunkMap(lockData.wcpWriteMap);
			lockData.wdcReadMap = getPotentiallyShrunkMap(lockData.wdcReadMap);
			lockData.wdcWriteMap = getPotentiallyShrunkMap(lockData.wdcWriteMap);
		}
		
		// Do the increments last
		//TODO: Accessed by only this thread
		hb.inc(td.getTid());
		//wcp.inc(td.getTid()); // Don't increment WCP since it doesn't include PO
		if (!HB_WCP_ONLY) wdc.inc(td.getTid());
		
		//Set latest Release node for lockData
		lockData.latestRelNode = thisEventNode;
	}
	
	static <K,V> WeakIdentityHashMap<K,V>getPotentiallyShrunkMap(WeakIdentityHashMap<K,V> map) {
		if (map.tableSize() > 16 &&
		    10 * map.size() < map.tableSize() * map.loadFactorSize()) {
			return new WeakIdentityHashMap<K,V>(2 * (int)(map.size() / map.loadFactorSize()), map);
		}
		return map;
	}
	
	public static boolean readFastPath(final ShadowVar orig, final ShadowThread td) {
		if (orig instanceof WDCGuardState) {
			WDCGuardState x = (WDCGuardState)orig;
			ShadowLock lock = td.getInnermostLock();
			if (lock != null) {
				WDCLockData lockData = get(lock);
				if (!lockData.readVars.contains(x) && !lockData.writeVars.contains(x) && !HB_ONLY) {
					return false;
				}
			}
			
			synchronized(td) {	
				final CV tdHB = ts_get_hb(td);
				if (x.hbRead.get(td.getTid()) == tdHB.get(td.getTid()) - (BUILD_EVENT_GRAPH ? 1 : 0)) {
					if (COUNT_EVENT) readFP.inc(td.getTid());
					return true;
				}
			}
		} else {
			if (VERBOSE) Assert.assertTrue(false); // Not expecting to reach here
		}
		return false;
	}
	
	public static boolean writeFastPath(final ShadowVar orig, final ShadowThread td) {
		if (orig instanceof WDCGuardState) {
			WDCGuardState x = (WDCGuardState)orig;
			ShadowLock lock = td.getInnermostLock();
			if (lock != null) {
				WDCLockData lockData = get(lock);
				if (!lockData.writeVars.contains(x) && !HB_ONLY) {
					return false;
				}
			}
			
			synchronized(td) {	
				final CV tdHB = ts_get_hb(td);
				if (x.hbWrite.get(td.getTid()) == tdHB.get(td.getTid()) - (BUILD_EVENT_GRAPH ? 1 : 0)) {
					if (COUNT_EVENT) writeFP.inc(td.getTid());
					return true;
				}
			}
		} else {
			if (VERBOSE) Assert.assertTrue(false); // Not expecting to reach here
		}
		return false;
	}

	@Override
	public void access(final AccessEvent fae) {
		final ShadowVar orig = fae.getOriginalShadow();
		final ShadowThread td = fae.getThread();

		if (orig instanceof WDCGuardState) {

			synchronized(td) {
				if (COUNT_EVENT) total.inc(td.getTid());
				if (COUNT_EVENT) {
					if (fae.isWrite()) {
						if (COUNT_EVENT) write.inc(td.getTid());
					} else {
						if (COUNT_EVENT) read.inc(td.getTid());
					}
				}
				if (COUNT_EVENT) example.inc(td.getTid());

				WDCGuardState x = (WDCGuardState)orig;
				
				String fieldName = "";
				if (PRINT_EVENT || BUILD_EVENT_GRAPH) {
					if (EventNode.DEBUG_ACCESS_INFO) {
						if (fae instanceof FieldAccessEvent) {
							fieldName = ((FieldAccessEvent)fae).getInfo().getField().getName();						
						} else if (fae instanceof ArrayAccessEvent) {
							fieldName = Util.objectToIdentityString(fae.getTarget()) + "[" + ((ArrayAccessEvent)fae).getIndex() + "]";						
						}
					}
				}
				
				RdWrNode thisEventNode = null;
				if (BUILD_EVENT_GRAPH) {
					AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
					if (EventNode.DEBUG_ACCESS_INFO) {
						thisEventNode = new RdWrDebugNode(-2, -1, fae.isWrite(), fieldName, x, td.getTid(), currentCriticalSection);
					} else {
						thisEventNode = new RdWrNode(-2, -1, td.getTid(), currentCriticalSection);
					}
				}
				
				handleEvent(fae, thisEventNode);
				if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));

				final CV tdHB = ts_get_hb(td);
				CV tdWCP = null;
				if (!HB_ONLY) tdWCP = ts_get_wcp(td);
				CV tdWDC = null;
				if (!HB_WCP_ONLY) tdWDC = ts_get_wdc(td);
				int tid = td.getTid();

				// Even though we capture clinit edges via classAccessed(), it doesn't seem to capture quite everything.
				// In any case, FT2 also does the following in access() in addition to classAccessed().
				//TODO: Need to lock on classInitTime?
				Object target = fae.getTarget();
				if (target == null) {
					synchronized(classInitTime) { //Not sure what we discussed for classInit, but FT synchronizes on it so I assume the program executing does not protect accesses to classInit.
					WDCVolatileData initTime = classInitTime.get(((FieldAccessEvent)fae).getInfo().getField().getOwner());
					
					if (BUILD_EVENT_GRAPH) {
						if (initTime.wdc.anyGt(tdWDC)) {
							EventNode.addEdge(initTime.wdc.eventNode, thisEventNode);
						}
					}

					tdHB.max(initTime.hb);
					if (!HB_ONLY) tdWCP.max(initTime.hb); // union with HB since this is effectively a hard WCP edge
					if (!HB_WCP_ONLY) tdWDC.max(initTime.wdc);
					}
				}

				// WCP and WDC: Update variables accessed in critical sections for rule (a)
				for (int i = td.getNumLocksHeld() - 1; i >= 0; i--) {
					ShadowLock lock = td.getHeldLock(i);
					WDCLockData lockData = get(lock);
					// WCP: Account for conflicts with prior critical section instances
					CV priorCriticalSectionAfterWrite = null;
					if (!HB_ONLY) {
						priorCriticalSectionAfterWrite = lockData.wcpWriteMap.get(x);
						if (priorCriticalSectionAfterWrite != null) {
							tdWCP.max(priorCriticalSectionAfterWrite);
						}
						if (fae.isWrite()) {
							CV priorCriticalSectionAfterRead = lockData.wcpReadMap.get(x);
							if (priorCriticalSectionAfterRead != null) {
								tdWCP.max(priorCriticalSectionAfterRead);
							}
						}
					}
					// WDC: Account for conflicts with prior critical section instances
					if (!HB_WCP_ONLY) {
						priorCriticalSectionAfterWrite = lockData.wdcWriteMap.get(x);
						if (priorCriticalSectionAfterWrite != null) {
							
							if (BUILD_EVENT_GRAPH) {
								if (priorCriticalSectionAfterWrite.anyGt(tdWDC)) {
									EventNode.addEdge(((CVE)priorCriticalSectionAfterWrite).eventNode, thisEventNode);
								}
							}
							
							tdWDC.max(priorCriticalSectionAfterWrite);
						}
						if (fae.isWrite()) {
							CVE priorCriticalSectionAfterRead = lockData.wdcReadMap.get(x);
							if (priorCriticalSectionAfterRead != null) {
	
								if (BUILD_EVENT_GRAPH) {
									if (priorCriticalSectionAfterRead.anyGt(tdWDC)) {
										EventNode.addEdge(priorCriticalSectionAfterRead.eventNode, thisEventNode);
									}
								}
	
								tdWDC.max(priorCriticalSectionAfterRead);
							}
						}
					}
					// Keep track of accesses within ongoing critical section
					if (!HB_ONLY) {
						if (fae.isWrite()) {
							lockData.writeVars.add(x);
						} else {
							lockData.readVars.add(x);
						}
					}
				}
				
				synchronized(x) {
					//TODO: Have to lock on variable x here until the end of the access event
					// Check for WDC races, which might be WCP races, which might be HB races
					boolean foundRace = checkForRaces(fae.isWrite(), x, fae, tid, tdHB, tdWCP, tdWDC, thisEventNode);
					// Update thread VCs if WDC race detected (to correspond with WDC edge being added)
					if (foundRace) {
						tdHB.max(x.hbWrite);
						if (!HB_ONLY) tdWCP.max(x.hbWrite);
						if (!HB_WCP_ONLY) tdWDC.max(x.wdcWrite);
						if (fae.isWrite()) {
							tdHB.max(x.hbReadsJoined);
							if (!HB_ONLY) tdWCP.max(x.hbReadsJoined);
							if (!HB_WCP_ONLY) tdWDC.max(x.wdcReadsJoined);
						}
					} else {
						CV wcpUnionPO = null;
						if (!HB_ONLY) {
							wcpUnionPO = new CV(tdWCP);
							wcpUnionPO.set(tid, tdHB.get(tid));
						}
						// Check that we don't need to update CVs if there was no race)
						if (VERBOSE) Assert.assertTrue(!x.hbWrite.anyGt(tdHB));
						if (!HB_ONLY && VERBOSE) Assert.assertTrue(!x.wcpWrite.anyGt(wcpUnionPO));
						//Assert.assertTrue(!x.hbWrite.anyGt(wcpUnionPO));
						if (!HB_WCP_ONLY && VERBOSE) Assert.assertTrue(!x.wdcWrite.anyGt(tdWDC));
						if (fae.isWrite()) {
							if (VERBOSE) Assert.assertTrue(!x.hbReadsJoined.anyGt(tdHB));
							if (!HB_ONLY && VERBOSE) Assert.assertTrue(!x.wcpReadsJoined.anyGt(wcpUnionPO));
							//Assert.assertTrue(!x.hbReadsJoined.anyGt(wcpUnionPO));
							if (!HB_WCP_ONLY && VERBOSE) Assert.assertTrue(!x.wdcReadsJoined.anyGt(tdWDC));
						}
					}
	
					if (BUILD_EVENT_GRAPH) {
						// Can combine two consecutive write/read nodes that have the same VC.
						// We might later add an outgoing edge from the prior node, but that's okay.
						EventNode oldThisEventNode = thisEventNode;
						thisEventNode = thisEventNode.tryToMergeWithPrior();
						// If merged, undo prior increment
						if (thisEventNode != oldThisEventNode) {
//							tdPO.inc(tid, -1); //Don't rewind the partial order clock
							tdHB.inc(tid, -1);
							if (!HB_ONLY) tdWDC.inc(tid, -1);
							ts_set_lastEventNode(td, thisEventNode);
						}
					}
					
					final MethodEvent me = td.getBlockDepth() <= 0 ? null : td.getBlock(td.getBlockDepth()-1); //This is how RREventGenerator retrieves a method event
					DynamicSourceLocation dl = new DynamicSourceLocation(fae, thisEventNode, (me == null ? null : me.getInfo()));
					
					// Update HB
					if (fae.isWrite()) {
						x.hbWrite.assignWithResize(tdHB);
					} else {
						x.hbRead.set(tid, tdHB.get(tid));
						x.hbReadsJoined.max(tdHB);
					}
	
					// Update WCP
					if (!HB_ONLY) {
						CV wcpUnionPO = new CV(tdWCP);
						wcpUnionPO.set(tid, tdHB.get(tid));
						if (fae.isWrite()) {
							x.wcpWrite.assignWithResize(wcpUnionPO);
						} else {
							x.wcpRead.set(tid, tdHB.get(tid));
							x.wcpReadsJoined.max(wcpUnionPO);
						}
					}
					
					// Update WDC and last event
					if (fae.isWrite()) {
						if (!HB_WCP_ONLY) x.wdcWrite.assignWithResize(tdWDC);
						x.lastWriteTid = tid;
						x.lastWriteEvent = dl;
					} else {
						if (!HB_WCP_ONLY) {
							x.wdcRead.set(tid, tdWDC.get(tid));
							x.wdcReadsJoined.max(tdWDC);
						}
						x.lastReadEvents[tid] = dl;
					}
					
					// These increments are needed because we might end up creating an outgoing WDC edge from this access event
					// (if it turns out to be involved in a WDC-race).
					// Only if there is an outgoing WDC edge should the thread's CV be updated.
					if (BUILD_EVENT_GRAPH) {
						tdHB.inc(td.getTid());
						//wcp.inc(td.getTid()); // Don't increment WCP since it doesn't include PO
						if (!HB_WCP_ONLY) tdWDC.inc(td.getTid());
					}
				}
				
				if (PRINT_EVENT) {		
					if (fae.isWrite()) {
						Util.log("wr("+ fieldName +") by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
					} else {
						Util.log("rd("+ fieldName +") by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
					}
				}
			}
		} else {
			if (VERBOSE) Assert.assertTrue(false); // Not expecting to reach here
			super.access(fae);
		}
	}

	//TODO: This should be protected by the lock on variable x in the access event
	// Return true iff WDC-race found
	boolean checkForRaces(boolean isWrite, WDCGuardState x, AccessEvent ae, int tid, CV tdHB, CV tdWCP, CV tdWDC, RdWrNode thisEventNode) {
		CV wcpUnionPO = null;
		if (!HB_ONLY) {
			wcpUnionPO = new CV(tdWCP);
			wcpUnionPO.set(tid, tdHB.get(tid));
		}
		
		int shortestRaceTid = -1;
		boolean shortestRaceIsWrite = false; // only valid if shortestRaceTid != -1
		RaceType shortestRaceType = RaceType.WDCOrdered; // only valid if shortestRaceTid != -1

		// First check for race with prior write
		if (!HB_WCP_ONLY) {
			if (x.wdcWrite.anyGt(tdWDC)) {
				RaceType type = RaceType.WDCRace;
				if (x.wcpWrite.anyGt(wcpUnionPO)) {
					type = RaceType.WCPRace;
					if (x.hbWrite.anyGt(tdHB)) {
						type = RaceType.HBRace;
					}
				} else {
					if (VERBOSE) Assert.assertTrue(!x.hbWrite.anyGt(tdHB));
				}
				shortestRaceTid = x.lastWriteTid;
				shortestRaceIsWrite = true;
				shortestRaceType = type;
				// Add event node edge
				if (BUILD_EVENT_GRAPH) {
					EventNode.addEdge(x.lastWriteEvent.eventNode, thisEventNode);
				}
			} else {
				if (VERBOSE) Assert.assertTrue(!x.wcpWrite.anyGt(wcpUnionPO));//, "VC x: " + x.wcpWrite.toString() + "| VC wcpUnionPO: " + wcpUnionPO.toString());
				if (VERBOSE) Assert.assertTrue(!x.hbWrite.anyGt(tdHB));
			}
		} else if (!HB_ONLY) {
			if (x.wcpWrite.anyGt(wcpUnionPO)) {
				RaceType type = RaceType.WCPRace;
				if (x.hbWrite.anyGt(tdHB)) {
					type = RaceType.HBRace;
				}
				shortestRaceTid = x.lastWriteTid;
				shortestRaceIsWrite = true;
				shortestRaceType = type;
			} else {
				if (VERBOSE) Assert.assertTrue(!x.hbWrite.anyGt(tdHB));
			}
		} else {
			if (x.hbWrite.anyGt(tdHB)) {
				RaceType type = RaceType.HBRace;
				shortestRaceTid = x.lastWriteTid;
				shortestRaceIsWrite = true;
				shortestRaceType = type;
			}
		}
		
		// Next check for races with prior reads
		if (!HB_WCP_ONLY) {
			if (isWrite) {
				if (x.wdcRead.anyGt(tdWDC)) {
					int index = -1;
					while ((index = x.wdcRead.nextGt(tdWDC, index + 1)) != -1) {
						RaceType type = RaceType.WDCRace;
						if (x.wcpRead.get(index) > wcpUnionPO.get(index)) {
							type = RaceType.WCPRace;
							if (x.hbRead.get(index) > tdHB.get(index)) {
								type = RaceType.HBRace;
							}
						} else {
							if (VERBOSE) Assert.assertTrue(x.hbRead.get(index) <= tdHB.get(index));
						}
						//Update the latest race with the current race since we only want to report one race per access event
						DynamicSourceLocation dl = shortestRaceTid >= 0 ? (shortestRaceIsWrite ? x.lastWriteEvent : x.lastReadEvents[shortestRaceTid]) : null;
						if (!BUILD_EVENT_GRAPH || (dl == null || x.lastReadEvents[index].eventNode.eventNumber > dl.eventNode.eventNumber)) {
							shortestRaceTid = index;
							shortestRaceIsWrite = false;
							shortestRaceType = type;
						}
						if (VERBOSE) Assert.assertTrue(x.lastReadEvents[index] != null);//, "lastReadEvents x: " + x.lastReadEvents.toString() + ", index " + index + " is null");
						if (BUILD_EVENT_GRAPH && VERBOSE) Assert.assertTrue(x.lastReadEvents[index].eventNode != null);//, "lastReadEvents x: " + x.lastReadEvents.toString() + ", index " + index + " containing dl: " + x.lastReadEvents[index].toString() + " has null event node.");
						if (BUILD_EVENT_GRAPH) {
							// This thread's last reader node might be same as the last writer node, due to merging
							//TODO: Tomcat fails if x.lastWriteEvent not checked for null. This would happen if only reads came before the first write? 
							if (x.lastWriteEvent != null && x.lastReadEvents[index].eventNode == x.lastWriteEvent.eventNode) {
								if (VERBOSE) Assert.assertTrue(EventNode.edgeExists(x.lastWriteEvent.eventNode, thisEventNode));
							} else {
								EventNode.addEdge(x.lastReadEvents[index].eventNode, thisEventNode);
							}
						}
					}
				} else {
					if (VERBOSE) Assert.assertTrue(!x.wcpRead.anyGt(wcpUnionPO));//, "VC x: " + x.wcpRead.toString() + "| VC wcpUnionPO: " + wcpUnionPO.toString());
					if (VERBOSE) Assert.assertTrue(!x.hbRead.anyGt(tdHB));
				}
			}
		} else if (!HB_ONLY) {
			if (isWrite) {
				if (x.wcpRead.anyGt(wcpUnionPO)) {
					int index = -1;
					while ((index = x.wcpRead.nextGt(wcpUnionPO, index + 1)) != -1) {
						RaceType type = RaceType.WCPRace;
						if (x.hbRead.get(index) > tdHB.get(index)) {
							type = RaceType.HBRace;
						}						
						// Update the latest race with the current race since we only want to report one race per access event
						shortestRaceTid = index;
						shortestRaceIsWrite = false;
						shortestRaceType = type;
					}
				} else {
					if (VERBOSE) Assert.assertTrue(!x.hbRead.anyGt(tdHB));
				}
			}
		} else {
			if (isWrite) {
				if (x.hbRead.anyGt(tdHB)) {
					int index = -1;
					while ((index = x.hbRead.nextGt(tdHB, index + 1)) != -1) {
						RaceType type = RaceType.HBRace;
						shortestRaceTid = index;
						shortestRaceIsWrite = false;
						shortestRaceType = type;
					}
				}
			}
		}
		
		if (shortestRaceTid >= 0) {
			DynamicSourceLocation priorDL = (shortestRaceIsWrite ? x.lastWriteEvent : x.lastReadEvents[shortestRaceTid]);
			// Report race
			error(ae, shortestRaceType.relation(), shortestRaceIsWrite ? "write by T" : "read by T", shortestRaceTid, priorDL,
			      ae.isWrite() ? "write by T" : "read by T", tid);

			StaticRace staticRace;
			// Record the WDC-race for later processing
			if (BUILD_EVENT_GRAPH) {
				// Check assertions:
				//This assertion is checked in crazyNewEdges() when adding back/forward edges. It fails here, but not when checked in crazyNewEdges().
				//Both traversals below are forward traversals, so sinkOrSinks is accessed during the bfsTraversal. The bfsTraversal is not executed atomically which causes the assertion to fail at different event counts. 
				if (false) {
					EventNode.removeEdge((RdWrNode)priorDL.eventNode, thisEventNode);
					Assert.assertTrue(!EventNode.bfsTraversal((RdWrNode)priorDL.eventNode, thisEventNode, null, Long.MIN_VALUE, Long.MAX_VALUE, false), "prior Tid: " + priorDL.eventNode.threadID + " | this Tid: " + thisEventNode.threadID);
					Assert.assertTrue(!EventNode.bfsTraversal(thisEventNode, (RdWrNode)priorDL.eventNode, null, Long.MIN_VALUE, Long.MAX_VALUE, false));
					EventNode.addEdge((RdWrNode)priorDL.eventNode, thisEventNode);
				}

				ShadowThread td = ae.getThread();
				final MethodEvent me = td.getBlockDepth() <= 0 ? null : td.getBlock(td.getBlockDepth()-1); //This is how RREventGenerator retrieves a method event
				staticRace = new StaticRace(priorDL.loc, ae.getAccessInfo().getLoc(), (RdWrNode)priorDL.eventNode, thisEventNode, shortestRaceType, priorDL.eventMI, me.getInfo());
				StaticRace.wdcRaces.add(staticRace);
			} else {
				staticRace = new StaticRace(priorDL.loc, ae.getAccessInfo().getLoc());
			}
			
			// Record the static race for statistics
			StaticRace.addRace(staticRace, shortestRaceType);
			
			return true;
		}
		return false;
	}	
	
	@Override
	public void volatileAccess(final VolatileAccessEvent fae) {
		final ShadowThread td = fae.getThread();
		synchronized(td) {
			//final ShadowVar orig = fae.getOriginalShadow();
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) {
				if (fae.isWrite()) {
					volatile_write.inc(td.getTid());
				} else {
					volatile_read.inc(td.getTid());
				}
			}			
			

			EventNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) { //Volatiles are treated the same as non-volatile rd/wr accesses since each release does not have a corresponding acquire
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new EventNode(-2, -1, fae.getThread().getTid(), currentCriticalSection, "volatileAccess");
//				thisEventNode = new VolNode(event_count, -1, fae.getThread().getTid(), fae.isWrite(), fieldName, currentCriticalSection);
			}
			handleEvent(fae, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
			
			if (PRINT_EVENT) {
				String fieldName = fae.getInfo().getField().getName();
				if (fae.isWrite()) {
					Util.log("volatile wr("+ fieldName +") by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
				} else {
					Util.log("volatile rd("+ fieldName +") by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
				}
			}

			//TODO: lock on the volatile variable vd
			WDCVolatileData vd = get(fae.getShadowVolatile());
			synchronized(vd) {
			final CV hb = ts_get_hb(td);
			CV wcp = null;
			if (!HB_ONLY) wcp = ts_get_wcp(td);
			CV wdc = null;
			if (!HB_WCP_ONLY) wdc = ts_get_wdc(td);
			if (fae.isWrite()) {
				int tid = td.getTid();
				vd.hb.max(hb);
				if (!HB_ONLY) vd.wcp.max(hb);
				hb.inc(tid);
				//wcp.inc(tid); // Don't increment since WCP doesn't include PO
				if (!HB_WCP_ONLY) {
					vd.wdc.max(wdc);
					vd.wdcWrites.set(tid, wdc.get(tid));
				}
				vd.lastWriteEvents[tid] = thisEventNode;
				if (!HB_WCP_ONLY) wdc.inc(tid);
			} else {
				hb.max(vd.hb);
				if (!HB_ONLY) wcp.max(vd.hb); // Union with HB since a volatile write-read edge is effectively a hard WCP edge
				// Hard WDC constraint
				if (BUILD_EVENT_GRAPH) {
					if (vd.wdcWrites.anyGt(wdc)) {
						int index = -1;
						while ((index = vd.wdcWrites.nextGt(wdc, index + 1)) != -1) {
							EventNode.addEdge(vd.lastWriteEvents[index], thisEventNode);
						}
					}
				}
				if (!HB_WCP_ONLY) wdc.max(vd.wdc);
			}
			}
		}
			
		super.volatileAccess(fae);
	}

	private void error(final AccessEvent ae, final String relation, final String prevOp, final int prevTid, final DynamicSourceLocation prevDL, final String curOp, final int curTid) {


		try {		
			if (ae instanceof FieldAccessEvent) {
				FieldAccessEvent fae = (FieldAccessEvent)ae;
				final FieldInfo fd = fae.getInfo().getField();
				final ShadowThread currentThread = fae.getThread();
				final Object target = fae.getTarget();
				
				fieldErrors.error(currentThread,
						fd,
						"Relation",						relation,
						"Guard State", 					fae.getOriginalShadow(),
						"Current Thread",				toString(currentThread), 
						"Class",						target==null?fd.getOwner():target.getClass(),
						"Field",						Util.objectToIdentityString(target) + "." + fd, 
						"Prev Op",						prevOp + prevTid,
						"Prev Loc",						prevDL == null ? "?" : prevDL.toString(),
						"Prev Event #",					prevDL == null ? "?" : prevDL.eventNode,
						"Prev Example #",				(prevDL == null || prevDL.eventNode == null) ? "?" : prevDL.eventNode.getExampleNumber(),
						"Cur Op",						curOp + curTid,
						"Current Event #",				ts_get_lastEventNode(currentThread)==null?"null":ts_get_lastEventNode(currentThread).eventNumber,
						//"Case", 						"#" + errorCase,
						"Stack",						ShadowThread.stackDumpForErrorMessage(currentThread) 
				);
				if (!fieldErrors.stillLooking(fd)) {
					advance(ae);
					return;
				}
			} else {
				ArrayAccessEvent aae = (ArrayAccessEvent)ae;
				final ShadowThread currentThread = aae.getThread();
				final Object target = aae.getTarget();

				arrayErrors.error(currentThread,
						aae.getInfo(),
						"Relation",						relation,
						"Alloc Site", 					ArrayAllocSiteTracker.get(aae.getTarget()),
						"Guard State", 					aae.getOriginalShadow(),
						"Current Thread",				currentThread==null?"":toString(currentThread), 
						"Array",						Util.objectToIdentityString(target) + "[" + aae.getIndex() + "]",
						"Prev Op",						ShadowThread.get(prevTid)==null?"":prevOp + prevTid + ("name = " + ShadowThread.get(prevTid).getThread().getName()),
						"Prev Loc",						prevDL == null ? "?" : prevDL.toString(),
						"Prev Event #",					prevDL == null ? "?" : prevDL.eventNode,
						"Cur Op",						ShadowThread.get(curTid)==null?"":curOp + curTid + ("name = " + ShadowThread.get(curTid).getThread().getName()), 
						"Current Event #",				ts_get_lastEventNode(currentThread)==null?"null":ts_get_lastEventNode(currentThread).eventNumber,
						//"Case", 						"#" + errorCase,
						"Stack",						ShadowThread.stackDumpForErrorMessage(currentThread) 
				);
				//Raptor: added ShadowThread.get(prevTid[curTid])==null?"": to "Prev[Cur] Op" and "Current Thread" since null pointers where being thrown due to the fact 
				//that a shadow thread would appear empty for a prevOp.

				aae.getArrayState().specialize();

				if (!arrayErrors.stillLooking(aae.getInfo())) {
					advance(aae);
					return;
				}
			}
		} catch (Throwable e) {
			Assert.panic(e);
		}
	}

	@Override
	public void preStart(final StartEvent se) {
		final ShadowThread td = se.getThread();
		synchronized(td) {
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) start.inc(td.getTid());
			
			

			EventNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) { //preStart is handled the same as rd/wr accesses
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new EventNode(-2, -1,  td.getTid(), currentCriticalSection, "start");
			}
			handleEvent(se, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));

			if (PRINT_EVENT) {
				Util.log("preStart by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
			}
			
			//TODO: the forked thread has not started yet, so there should be no need to lock
			final ShadowThread forked = se.getNewThread();
			int thisTid = td.getTid();
			int forkedTid = forked.getTid();

			final CV tdHB = ts_get_hb(td);
			CV tdWCP = null;
			if (!HB_ONLY) tdWCP = ts_get_wcp(td);
			CV tdWDC = null;
			if (!HB_WCP_ONLY) tdWDC = ts_get_wdc(td);
			final CV forkedHB = ts_get_hb(forked);
			CV forkedWCP = null;
			if (!HB_ONLY) forkedWCP = ts_get_wcp(forked);
			CV forkedWDC = null;
			if (!HB_WCP_ONLY) forkedWDC = ts_get_wdc(forked);
			
			// Compute WCP before modifying tdHB
			if (!HB_ONLY) forkedWCP.max(tdHB); // Use HB here because this is a hard WCP edge
			//forkedWCP.inc(forkedTid); // Not needed since create() does an increment
			//tdWCP.inc(thisTid); // Don't do increment since WCP doesn't include PO

			forkedHB.max(tdHB);
			//forkedHB.inc(forkedTid); // Not needed since create() does an increment
			tdHB.inc(thisTid);

			if (!HB_WCP_ONLY) {
				forkedWDC.max(tdWDC);
				ts_set_wdc(forked, new CVE(forkedWDC, thisEventNode)); // for generating event node graph
				//forkedWDC.inc(forkedTid); // Not needed since create() does an increment
				tdWDC.inc(thisTid);
			}
		}

		super.preStart(se);
	}

	@Override
	public void postJoin(final JoinEvent je) {
		final ShadowThread td = je.getThread();
		synchronized(td) {
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) join.inc(td.getTid());

			EventNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) { //postJoin is handled the same as rd/wr accesses
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new EventNode(-2, -1, td.getTid(), currentCriticalSection, "join");
			}
			handleEvent(je, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));

			//TODO: thread is already joined so there should be no need to lock
			final ShadowThread joining = je.getJoiningThread();

			// this test tells use whether the tid has been reused already or not.  Necessary
			// to still account for stopped thread, even if that thread's tid has been reused,
			// but good to know if this is happening alot...
			if (joining.getTid() == -1) {
				Yikes.yikes("Joined after tid got reused --- don't touch anything related to tid here!");
			}
			
			if (BUILD_EVENT_GRAPH) {
				EventNode priorNode = ts_get_lastEventNode(joining);
				EventNode.addEdge(priorNode, thisEventNode);
			}
			
			if (PRINT_EVENT) {
				Util.log("postJoin by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
			}
			
			//joiningHB.inc(joiningTid); // Mike says: I don't see why we'd every want to do this here; instead, the terminating (joining) thread should do it
			ts_get_hb(td).max(ts_get_hb(joining));
			if (!HB_ONLY) ts_get_wcp(td).max(ts_get_hb(joining)); // Use HB since this is a hard WCP edge
			if (!HB_WCP_ONLY) ts_get_wdc(td).max(ts_get_wdc(joining));
		}

		super.postJoin(je);	
	}


	@Override
	public void preNotify(NotifyEvent we) {
		super.preNotify(we);
	}

	@Override
	public void preWait(WaitEvent we) {
		final ShadowThread td = we.getThread();
		synchronized (td) {
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) preWait.inc(td.getTid());
			
			AcqRelNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) {
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new AcqRelNode(-2, -1, we.getLock(), td.getTid(), false, currentCriticalSection);
				updateCurrentCriticalSectionAtRelease(td, thisEventNode);
			}
			handleEvent(we, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));

			if (PRINT_EVENT) {
				Util.log("preWait by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
			}
			
			//TODO: lock is already held
			handleRelease(td, we.getLock(), thisEventNode);
		}

		// Mike says: isn't the following (original FastTrack code) doing an inc on the thread's clock before using it for max, which is wrong?
		// Or maybe it doesn't matter since this thread doesn't do any accesses between preWait and postWait.
		/*
		this.incEpochAndCV(we.getThread(), we);
		synchronized(lockData) {
			lockData.cv.max(ts_get_cv(we.getThread()));
		}
		*/
		
		super.preWait(we);
	}

	@Override
	public void postWait(WaitEvent we) {
		final ShadowThread td = we.getThread();
		synchronized (td) {
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) postWait.inc(td.getTid());
			
			AcqRelNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) {
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new AcqRelNode(-2, -1, we.getLock(), td.getTid(), true, currentCriticalSection);
				updateCurrentCriticalSectionAtAcquire(td, thisEventNode);
			}
			handleEvent(we, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
			
			if (PRINT_EVENT) {
				Util.log("postWait by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
			}
			
			//TODO: lock is already held
			// Considering wait--notify to be a hard WCP and WDC edge.
			// (If wait--notify is used properly, won't it already be a hard edge?)
			handleAcquire(td, we.getLock(), thisEventNode, true);
		}

		super.postWait(we);
	}

	public static String toString(final ShadowThread td) {
		return String.format("[tid=%-2d   hb=%s   wcp=%s   wdc=%s]", td.getTid(), ts_get_hb(td), ts_get_wcp(td), (!HB_WCP_ONLY ? ts_get_wdc(td) : "N/A"));
	}

	private final Decoration<ShadowThread, CV> cvForExit = 
		ShadowThread.makeDecoration("WDC:barrier", DecorationFactory.Type.MULTIPLE, new NullDefault<ShadowThread, CV>());

	public void preDoBarrier(BarrierEvent<WDCBarrierState> be) {
		Assert.assertTrue(false); // Does this ever get triggered in our evaluated programs?
		WDCBarrierState wdcBE = be.getBarrier();
		ShadowThread currentThread = be.getThread();
		CV entering = wdcBE.getEntering();
		entering.max(ts_get_hb(currentThread));
		cvForExit.set(currentThread, entering);
	}

	public void postDoBarrier(BarrierEvent<WDCBarrierState> be) {
		Assert.assertTrue(false); // Does this ever get triggered in our evaluated programs?
		WDCBarrierState wdcBE = be.getBarrier();
		ShadowThread currentThread = be.getThread();
		CV old = cvForExit.get(currentThread);
		wdcBE.reset(old);
		ts_get_hb(currentThread).max(old);
		ts_get_wcp(currentThread).max(old); // Also update WCP since a barrier is basically an all-to-all hard WCP edge
		if (!HB_WCP_ONLY) ts_get_wdc(currentThread).max(old); // Updating WDC to HB seems fine at a barrier (?)
	}

	@Override
	public void classInitialized(ClassInitializedEvent e) {
		final ShadowThread td = e.getThread();
		synchronized (td) {
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) class_init.inc(td.getTid());
			
			EventNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) { //classInitialized is handled the same as rd/wr accesses
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new EventNode(-2, -1, td.getTid(), currentCriticalSection, "initialize");
			}
			handleEvent(e, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
			
			if (PRINT_EVENT) {
				Util.log("classInitialized by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
			}
		
			final CV hb = ts_get_hb(td);
			CV wcp = null;
			if (!HB_ONLY) wcp = ts_get_wcp(td);
			CV wdc = null;
			if (!HB_WCP_ONLY) wdc = ts_get_wdc(td);
			int tid = td.getTid();

			synchronized(classInitTime) { //Not sure what we discussed for classInit, but FT synchronizes on it so I assume the program executing does not protect accesses to classInit. 
			//TODO: lock on the class? I think it needs to since access event will access classInitTime.
			//Util.log("Class Init for " + e + " -- " + cv);
			classInitTime.get(e.getRRClass()).hb.max(hb);
			hb.inc(tid);
			if (!HB_ONLY) classInitTime.get(e.getRRClass()).wcp.max(wcp);
			//wcp.inc(tid); // Don't do inc since WCP doesn't include PO
			if (!HB_WCP_ONLY) {
				classInitTime.get(e.getRRClass()).wdc.max(wdc);
				classInitTime.get(e.getRRClass()).wdc.setEventNode(thisEventNode);
				wdc.inc(tid);
			}
			}
		}

		super.classInitialized(e);
	}
	
	@Override
	public void classAccessed(ClassAccessedEvent e) {
		final ShadowThread td = e.getThread();
		synchronized(td) {
			
			if (COUNT_EVENT) total.inc(td.getTid());
			if (COUNT_EVENT) class_accessed.inc(td.getTid());
			
			EventNode thisEventNode = null;
			if (BUILD_EVENT_GRAPH) { //classInitialized is handled the same as rd/wr accesses
				AcqRelNode currentCriticalSection = getCurrentCriticalSection(td);
				thisEventNode = new EventNode(-2, -1, td.getTid(), currentCriticalSection, "class_accessed");
			}
			handleEvent(e, thisEventNode);
			if (VERBOSE && BUILD_EVENT_GRAPH) Assert.assertTrue(thisEventNode.eventNumber > -2 || td.getThread().getName().equals("Finalizer"));
			
			if (PRINT_EVENT) {
				Util.log("classAccessed by T"+td.getTid()+(BUILD_EVENT_GRAPH ? ", event count:"+thisEventNode.eventNumber : ""));
			}
			
			final CV hb = ts_get_hb(td);
			CV wcp = null;
			if (!HB_ONLY) wcp = ts_get_wcp(td);
			CV wdc = null;
			if (!HB_WCP_ONLY) wdc = ts_get_wdc(td);
			
			synchronized(classInitTime) { //Not sure what we discussed for classInit, but FT synchronizes on it so I assume the program executing does not protect accesses to classInit.
				//TODO: lock on the class?
				WDCVolatileData initTime = classInitTime.get(e.getRRClass());
					
				if (BUILD_EVENT_GRAPH) {
					if (initTime.wdc.anyGt(wdc)) {
						EventNode.addEdge(initTime.wdc.eventNode, thisEventNode);
					}
				}
	
				hb.max(initTime.hb);
				if (!HB_ONLY) wcp.max(initTime.hb); // union with HB since this is effectively a hard WCP edge
				if (!HB_WCP_ONLY) wdc.max(initTime.wdc);
			}
		}
	}

	@Override
	public void printXML(XMLWriter xml) {
		for (ShadowThread td : ShadowThread.getThreads()) {
			xml.print("thread", toString(td));
		}
	}

	public File storeReorderedTraces() {
		File commandDir = null;
		if (RR.wdcbPrintReordering.get()) {
			File traceDir = new File("WDC_Traces");
			if (!traceDir.exists()) {
				traceDir.mkdir();
			}
			int version = 1;
			String commandDirName = traceDir + "/" + CommandLine.javaArgs.get().trim();
			commandDir = new File(commandDirName + "_" + version);
			while (commandDir.exists()) {
				version++;
				commandDir = new File(commandDirName + "_" + version);
			}
			commandDir.mkdir();
		}
		return commandDir;
	}
}

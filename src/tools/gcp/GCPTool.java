package tools.gcp;

import java.util.WeakHashMap;

import rr.annotations.Abbrev;
import rr.error.ErrorMessage;
import rr.error.ErrorMessages;
import rr.event.AccessEvent;
import rr.event.AcquireEvent;
import rr.event.ArrayAccessEvent;
import rr.event.Event;
import rr.event.FieldAccessEvent;
import rr.event.JoinEvent;
import rr.event.ReleaseEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.instrument.classes.ArrayAllocSiteTracker;
import rr.meta.ArrayAccessInfo;
import rr.meta.FieldInfo;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.tool.RR;
import rr.tool.Tool;
import acme.util.Assert;
import acme.util.Util;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DefaultValue;
import acme.util.option.CommandLine;

@Abbrev("GCP")
public class GCPTool extends Tool {
	static final Object eventLock = new Object();
	static int Verbose;
	static boolean PrintStats;
	static boolean HBMode;
	static boolean HBPure;
	static boolean StaticInit;
	static GCPStats Stats;
	static final ErrorMessage<FieldInfo> fieldErrors = ErrorMessages.makeFieldErrorMessage("Raptor");
	static final ErrorMessage<ArrayAccessInfo> arrayErrors = ErrorMessages.makeArrayErrorMessage("Raptor");
	
	public GCPTool(String name, Tool next, CommandLine commandLine) {
		super(name, next, commandLine);
	}
	
	static final Decoration<ShadowLock,Lock> gcpLock = ShadowLock.makeDecoration("GCP:ShadowLock", DecorationFactory.Type.MULTIPLE,
			new DefaultValue<ShadowLock,Lock>() { public Lock get(final ShadowLock ld) { return new Lock(ld); }});
	
	static final Decoration<ShadowThread, Thr> gcpThread = ShadowThread.makeDecoration("GCP:ShadowThread", DecorationFactory.Type.MULTIPLE,
			new DefaultValue<ShadowThread,Thr>() { public Thr get(final ShadowThread ld) { return new Thr(ld); }});

	static final Lock get(final ShadowLock ld) {
		return gcpLock.get(ld);
	}
	
	protected ShadowVar createHelper(AccessEvent e) {
		if (GCPTool.PrintStats) GCPStats.writeEventCount++;
		return new WriteVar((AccessEvent)e.clone());
	}
	
	@Override
	final public ShadowVar makeShadowVar(final AccessEvent fae) {
		synchronized(eventLock) {
			return createHelper(fae); //volatile or not
		}
	}
	
	@Override
	public void init() {
		Stats = new GCPStats();
		PrintStats = RR.raptorPrintStats.get(); 
		if (PrintStats) {
			Util.addToPeriodicTasks(new GCPStats.periodicEvents("Event Tracker", 60000)); //1 min
		}
		HBMode = RR.raptorHBModeOption.get();
		HBPure = RR.raptorHBPureOption.get();
		StaticInit = !RR.raptorStaticInitializerOption.get();
		if (!HBMode && !HBPure && GCPEvents.printLatexTrace) {
			GCPEvents.initializeLatex();
		}
	}
	
	@Override
	public void fini() {
		Stats.printEventCounts();
		Stats.printLocksetObjectCounts();
		if (!HBMode && !HBPure) {
			GCPEvents.closeLatex();
			GCPEvents.closeTraceWriters(GCPEvents.eventCounter++);
			GCPEvents.checkRaces();
		}
	}

	@Override
	public void acquire(final AcquireEvent ae) {
		final ShadowThread td = ae.getThread();
		final ShadowLock shadowLock = ae.getLock();

		synchronized(eventLock) {
			if (HBMode) {
				HBEvents.acquire(td, get(shadowLock), shadowLock);
			} else if (HBPure) {
				HBPureEvents.acquire(td, get(shadowLock), shadowLock);
			} else {
				GCPEvents.acquire(td, get(shadowLock), shadowLock);
			}
			Stats.trackEventOrder(ae);
		}
		super.acquire(ae);
	}
	
	@Override
	public void postWait(rr.event.WaitEvent we) {
		final ShadowThread td = we.getThread();
		final ShadowLock shadowLock = we.getLock();

		synchronized(eventLock) {
			if (HBMode) {
				HBEvents.acquire(td, get(shadowLock), shadowLock);
			} else if (HBPure) {
				HBPureEvents.acquire(td, get(shadowLock), shadowLock);
			} else {
				GCPEvents.acquire(td, get(shadowLock), shadowLock);
			}
			Stats.trackEventOrder(we);
		}
		super.postWait(we);
	}
	
	@Override
	public void release(final ReleaseEvent re) {
		final ShadowThread td = re.getThread();
		final ShadowLock shadowLock = re.getLock();
	
		synchronized(eventLock) {
			if (HBMode) {
				HBEvents.release(td, get(shadowLock));
			} else if (HBPure) {
				HBPureEvents.release(td, get(shadowLock));
			} else {
				GCPEvents.release(td, get(shadowLock), shadowLock);				
			}
			Stats.trackEventOrder(re);
		}
		super.release(re);
	}
	
	@Override
	public void preWait(rr.event.WaitEvent we) {
		final ShadowThread td = we.getThread();
		final ShadowLock shadowLock = we.getLock();
	
		synchronized(eventLock) {
			if (HBMode) {
				HBEvents.release(td, get(shadowLock));
			} else if (HBPure) {
				HBPureEvents.release(td, get(shadowLock));
			} else {
				GCPEvents.release(td, get(shadowLock), shadowLock);				
			}
			Stats.trackEventOrder(we);
		}
		super.preWait(we);
	}
	
	@Override
	public void access(final AccessEvent fae) {
		try {
		final ShadowVar orig = fae.getOriginalShadow();
		final ShadowThread td = fae.getThread();

		synchronized(eventLock) {
			if (orig instanceof Var) {
				Var x = (Var)orig;
				if (fae instanceof FieldAccessEvent) {
					if (((FieldAccessEvent)fae).getInfo().getEnclosing().toString().contains("<clinit>") && StaticInit && !x.classInit) {
						x.classInit = true;
						x.setClassInitLock(new Lock(new ShadowLock(new Object()), 0, ((FieldAccessEvent)fae).getInfo().getEnclosing().hashCode()));
					}
				} else {
					if (((ArrayAccessEvent)fae).getInfo().getEnclosing().toString().contains("<clinit>") && StaticInit && !x.classInit) {
						x.classInit = true;
						x.setClassInitLock(new Lock(new ShadowLock(new Object()), 0, ((ArrayAccessEvent)fae).getInfo().getEnclosing().hashCode()));
					}
				}
				AccessEvent fea = (AccessEvent)fae.clone();
				
				if (StaticInit && x.classInit && !x.classInitRemoveLock) {
					if (HBMode) {
						HBEvents.acquire(td, x.getClassInitLock(), x.getClassInitLock().shadowLock);
					} else if (HBPure) {
						HBPureEvents.acquire(td, x.getClassInitLock(), x.getClassInitLock().shadowLock);
					} else {
						GCPEvents.acquire(td, x.getClassInitLock(), x.getClassInitLock().shadowLock);
					}
					if (GCPTool.PrintStats) GCPStats.artificialAcquireEventCount++;
				}
				if (fae.isWrite()) {
					if (HBMode) {
						HBEvents.wri(td, x, fea, GCPStats.totalEventOrder);
					} else if (HBPure) {
						HBPureEvents.wri(td, x, fea, GCPStats.totalEventOrder);
					} else {
						GCPEvents.wri(td, x, fea, GCPStats.totalEventOrder);
					}
				} else {
					if (HBMode) {
						HBEvents.rd(td, x, fea, GCPStats.totalEventOrder);
					} else if (HBPure) {
						HBPureEvents.rd(td, x, fea, GCPStats.totalEventOrder);
					} else {
						GCPEvents.rd(td, x, fea, GCPStats.totalEventOrder);
					}
				}
				if (StaticInit && x.classInit && !x.classInitRemoveLock) {
					if (HBMode) {
						HBEvents.release(td, x.getClassInitLock());
					} else if (HBPure) {
						HBPureEvents.release(td, x.getClassInitLock());
					} else {
						GCPEvents.release(td, x.getClassInitLock(), x.getClassInitLock().shadowLock);
					}
					if (GCPTool.PrintStats) GCPStats.artificialReleaseEventCount++;
					if (GCPTool.PrintStats) GCPStats.staticEventCount++;
					if (fae.isWrite()) {
						if (fae instanceof FieldAccessEvent && !((FieldAccessEvent)fae).getInfo().getEnclosing().toString().contains("<clinit>")) {
							x.classInitRemoveLock = true;
						} else if (fae instanceof ArrayAccessEvent && !((ArrayAccessEvent)fae).getInfo().getEnclosing().toString().contains("<clinit>")) {
							x.classInitRemoveLock = true;
						}
					}
				}
				if (StaticInit && x.classInitRemoveLock) {
					LocksetLock.locksetLocks.remove(x.getClassInitLock());
					x.setClassInitLock(null);
				}
				Stats.trackEventOrder(fae);
			}
		}
		super.access(fae);
		} catch (Throwable e) {
			Assert.panic(e);
		}
	}
	
	@Override
	public void volatileAccess(final VolatileAccessEvent fae) {
		try {
		final ShadowVar orig = fae.getOriginalShadow();
		final ShadowThread td = fae.getThread();
			
			synchronized(eventLock) {
				Var x = (Var)orig;
				if (fae.isWrite()) {
					if (HBMode) {
						HBEvents.acquire(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
						if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
						HBEvents.wri(td, x, fae, GCPStats.totalEventOrder);
						HBEvents.release(td, x.getVolatileLock());
					} else if (HBPure) {
						HBPureEvents.acquire(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
						if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
						HBPureEvents.wri(td, x, fae, GCPStats.totalEventOrder);
						HBPureEvents.release(td, x.getVolatileLock());
					} else {
						GCPEvents.acquire(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
						if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
						GCPEvents.wri(td, x, fae, GCPStats.totalEventOrder);
						GCPEvents.release(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
					}
				} else {
					if (HBMode) {
						HBEvents.acquire(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
						if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
						HBEvents.rd(td, x, fae, GCPStats.totalEventOrder);
						HBEvents.release(td, x.getVolatileLock());
					} else if (HBPure) {
						HBPureEvents.acquire(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
						if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
						HBPureEvents.rd(td, x, fae, GCPStats.totalEventOrder);
						HBPureEvents.release(td, x.getVolatileLock());
					} else {
						GCPEvents.acquire(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
						if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
						GCPEvents.rd(td, x, fae, GCPStats.totalEventOrder);
						GCPEvents.release(td, x.getVolatileLock(), x.getVolatileLock().shadowLock);
					}
				}
				Stats.trackEventOrder(fae);
			}
		super.volatileAccess(fae);
		} catch (Throwable e) {
			Assert.panic(e);
		}
	}
	
	@Override
	public void preStart(final StartEvent se) {
		try {
		final ShadowThread td_P = se.getThread();
		final ShadowThread td_C = se.getNewThread();
		
		synchronized(eventLock) {
			if (HBMode) {
				WriteVar thr_CVar = new WriteVar(se);
				Lock start_join_lock = new Lock(new ShadowLock(new Object()), 0, thr_CVar.hashCode());
				HBEvents.acquire(td_P, start_join_lock, start_join_lock.shadowLock);
				HBEvents.release(td_P, start_join_lock);
				//creates a HB edge from the parent thread (creator) to the child thread (created)
				HBEvents.acquire(td_C, start_join_lock, start_join_lock.shadowLock);
				HBEvents.release(td_C, start_join_lock);
				LocksetVar.locksetVars.remove(thr_CVar);
			} else if (HBPure) {
				WriteVar thr_CVar = new WriteVar(se);
				Lock start_join_lock = new Lock(new ShadowLock(new Object()), 0, thr_CVar.hashCode());
				HBPureEvents.acquire(td_P, start_join_lock, start_join_lock.shadowLock);
				HBPureEvents.release(td_P, start_join_lock);
				//creates a HB edge from the parent thread (creator) to the child thread (created)
				HBPureEvents.acquire(td_C, start_join_lock, start_join_lock.shadowLock);
				HBPureEvents.release(td_C, start_join_lock);
				LocksetVar.locksetVars.remove(thr_CVar);
			} else {
				for (LocksetVar var : LocksetVar.locksetVars) {
					if (var.lockset.containsExactly(td_P, Type.HB) || var.lockset.containsExactly(td_P, Type.LS) || var.lockset.containsExactly(td_P, Type.CP)) {
						var.lockset.add(td_C, Type.CP);
					}
				}
				for (LocksetLock lock : LocksetLock.locksetLocks) {
					if (lock.lockset.containsExactly(td_P, Type.HB) || lock.lockset.containsExactly(td_P, Type.LS) || lock.lockset.containsExactly(td_P, Type.CP)) {
						lock.lockset.add(td_C, Type.CP);
					}
				}
				if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
			}
			
			Stats.trackEventOrder(se);
		}
		super.preStart(se);
		} catch (Throwable e) {
			Assert.panic(e);
		}
	}
	
	@Override
	public void postJoin(final JoinEvent je) {
		try {
		final ShadowThread td_P = je.getThread();
		final ShadowThread td_C = je.getJoiningThread();
		
		synchronized(eventLock) {
			if (HBMode) {
				WriteVar thr_CVar = new WriteVar(je);
				Lock start_join_lock = new Lock(new ShadowLock(new Object()), 0, thr_CVar.hashCode());
				HBEvents.acquire(td_C, start_join_lock, start_join_lock.shadowLock);
				HBEvents.release(td_C, start_join_lock);
				//creates a HB edge from the parent thread (creator) to the child thread (created)
				HBEvents.acquire(td_P, start_join_lock, start_join_lock.shadowLock);
				HBEvents.release(td_P, start_join_lock);
				LocksetVar.locksetVars.remove(thr_CVar);
			} else if (HBPure) {
				WriteVar thr_CVar = new WriteVar(je);
				Lock start_join_lock = new Lock(new ShadowLock(new Object()), 0, thr_CVar.hashCode());
				HBPureEvents.acquire(td_C, start_join_lock, start_join_lock.shadowLock);
				HBPureEvents.release(td_C, start_join_lock);
				//creates a HB edge from the parent thread (creator) to the child thread (created)
				HBPureEvents.acquire(td_P, start_join_lock, start_join_lock.shadowLock);
				HBPureEvents.release(td_P, start_join_lock);
				LocksetVar.locksetVars.remove(thr_CVar);
			} else {
				for (LocksetVar var : LocksetVar.locksetVars) {
					if (var.lockset.containsExactly(td_C, Type.HB) || var.lockset.containsExactly(td_C, Type.LS) || var.lockset.containsExactly(td_C, Type.CP)) {
						var.lockset.add(td_P, Type.CP);
					}
				}
				for (LocksetLock lock : LocksetLock.locksetLocks) {
					if (lock.lockset.containsExactly(td_C, Type.HB) || lock.lockset.containsExactly(td_C, Type.LS) || lock.lockset.containsExactly(td_C, Type.CP)) {
						lock.lockset.add(td_P, Type.CP);
					}
				}
				if (GCPTool.PrintStats) GCPStats.totalEventOrder++;
			}
			
			Stats.trackEventOrder(je);
		}
		super.postJoin(je);
		} catch (Throwable e) {
			Assert.panic(e);
		}
	}

	public static void error(Var x_i, ShadowThread x_iPlus1, String errorType) {
		try {
			Event x_iPlus1_Event = x_iPlus1==null?x_i.nextWrite_Event:x_i.nextRead_Event.get(x_iPlus1);
			long x_iPlus1_Dist = x_iPlus1==null?x_i.nextWrite_Dist:x_i.nextRead_Dist.get(x_iPlus1);
			if (x_iPlus1_Event instanceof FieldAccessEvent) {
				FieldAccessEvent fae = (FieldAccessEvent)x_iPlus1_Event;
				final ShadowThread nextThread = fae.getThread();
				final Object target = fae.getTarget();
				final FieldInfo fd = fae.getInfo().getField();
				
				FieldAccessEvent pae = (FieldAccessEvent)x_i.getEvent();
				final ShadowThread currentThread = pae.getThread();

				fieldErrors.error(nextThread,
						fd,
						"Error Type",					errorType,
						"Guard State", 					fae.getOriginalShadow(), 
						"Current Thread",				toString(currentThread),
						"Next Thread",					toString(nextThread),
						"Class",						target==null?fd.getOwner():target.getClass(),
						"Field",						Util.objectToIdentityString(target) + "." + fd, 
						"Cur Op",						(x_i instanceof WriteVar ? "Write -> " : "Read  -> ") + x_i.name + " instance #: " + x_i.version + " ; Lockset = " + x_i.lockset,
						"Next Op",						x_iPlus1==null?"Write":"Read",
						"Cur Loc",						pae.getAccessInfo().getLoc(),
						"Next Loc",						fae.getAccessInfo().getLoc(),
						"Current Event",				x_i.totalEventOrder,
						"Next Event",					x_iPlus1_Dist,
						"Distance",						(x_iPlus1_Dist - x_i.totalEventOrder),
						"Report Event",					GCPStats.totalEventOrder,
						"Stack",						nextThread==null?"":ShadowThread.stackDumpForErrorMessage(nextThread) 
				);
			} else if (x_iPlus1_Event instanceof ArrayAccessEvent) {
				ArrayAccessEvent aae = (ArrayAccessEvent)x_iPlus1_Event;
				final ShadowThread nextThread = aae.getThread();
				final Object target = aae.getTarget();
				
				ArrayAccessEvent pae = (ArrayAccessEvent)x_i.getEvent();
				final ShadowThread currentThread = pae.getThread(); 

				arrayErrors.error(nextThread,
						aae.getInfo(),
						"Error Type",					errorType,
						"Alloc Site", 					ArrayAllocSiteTracker.get(aae.getTarget()),
						"Guard State", 					aae.getOriginalShadow(),
						"Current Thread",				currentThread==null?"":toString(currentThread),
						"Next Thread",					nextThread==null?"":toString(nextThread),
						"Array",						Util.objectToIdentityString(target) + "[" + aae.getIndex() + "]",
						"Cur Op",						(x_i instanceof WriteVar ? "Write -> " : "Read -> ") + x_i.name + " instance #: " + x_i.version + " ; Lockset = " + x_i.lockset,
						"Next Op",						x_iPlus1==null?"Write":"Read",
						"Cur Loc",						pae.getAccessInfo().getLoc(),
						"Next Loc",						aae.getAccessInfo().getLoc(),
						"Current Event",				x_i.totalEventOrder,
						"Next Event",					x_iPlus1_Dist,
						"Distance",						(x_iPlus1_Dist - x_i.totalEventOrder),
						"Report Event",					GCPStats.totalEventOrder,
						"Stack",						nextThread==null?"":ShadowThread.stackDumpForErrorMessage(nextThread) 
				);

				aae.getArrayState().specialize();
			} else {
				Assert.fail("Errors must be reported as FieldAccessEvent or ArrayAccessEvent.\nTried reporting as: " + x_i.nextWrite_Event);
			}
		} catch (Throwable e) {
			Assert.panic(e);
		}
	}
	
	public static String toString(final ShadowThread td) {
		return String.format("[tid=%-2d]", td.getTid());
	}
}
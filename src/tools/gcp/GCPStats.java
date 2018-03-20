package tools.gcp;

import java.lang.ref.WeakReference;

import rr.event.AccessEvent;
import rr.event.AcquireEvent;
import rr.event.Event;
import rr.event.FieldAccessEvent;
import rr.event.JoinEvent;
import rr.event.ReleaseEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.tool.RR;
import acme.util.Util;
import acme.util.time.PeriodicTaskStmt;

public class GCPStats {
	static long totalEventOrder = 0;
	static long readEventCount = 0;
	static long writeEventCount = 0;
	static long artificialWriteEventCount = 0;
	static long acquireEventCount = 0;
	static long artificialAcquireEventCount = 0;
	static long releaseEventCount = 0;
	static long artificialReleaseEventCount = 0;
	static long volatileEventCount = 0;
	static long preStartEventCount = 0;
	static long postJoinEventCount = 0;
	static long staticEventCount = 0;
	static long writeLocksetCreation = 0;
	static long writeLocksetRemoved = 0;
	static long writeLocksetDestroyed = 0;
	static long readLocksetCreation = 0;
	static long readLocksetDestroyed = 0;
	static long readLocksetRemoved = 0;
	static long lockLocksetCreation = 0;
	static long lockLocksetRemoved = 0;
	static long lockLocksetDestroyed = 0;
	static long artificialLockLocksetCreation = 0;
	static long artificialLockLocksetDestroyed = 0;
	
	static long lastAcquireEvent = 0;
	static long lastReleaseEvent = 0;
	static long lastPreReleaseEvent = 0;
	static long lastWriteEvent = 0;
	static long lastReadEvent = 0;
	
	static long latestAcquireEvent = 0;
	
	static long timeAcquireEvent = 0;
	static long timeReleaseEvent = 0;
	static long timePreReleaseEvent = 0;
	static long timeWriteEvent = 0;
	static long timeReadEvent = 0;
	
	static long lastTimeAcquireEvent = 0;
	static long lastTimeReleaseEvent = 0;
	static long lastTimePreReleaseEvent = 0;
	static long lastTimeWriteEvent = 0;
	static long lastTimeReadEvent = 0;
	
	static double nano = 1000000000;
	
	public void printEventCounts() {
		Util.log("\\newcommand{\\RaptorReadEvents}{" + readEventCount + "}\n" +
				"\\newcommand{\\RaptorWriteEvents}{" + writeEventCount + "}\n" +
				"\\newcommand{\\RaptorAcquireEvents}{" + acquireEventCount + "}\n" +
				"\\newcommand{\\RaptorReleaseEvents}{" + releaseEventCount + "}\n" +
				"\\newcommand{\\RaptorTotalEvents}{" + totalEventOrder + "}\n" +
				"\\newcommand{\\RaptorArtifWriteEvents}{" + artificialWriteEventCount + "}\n" +
				"\\newcommand{\\RaptorArtifAcquireEvents}{" + artificialAcquireEventCount + "}\n" +
				"\\newcommand{\\RaptorArtifReleaseEvents}{" + artificialReleaseEventCount + "}\n" +
				"\\newcommand{\\RaptorVolatileEvents}{" + volatileEventCount + "}\n" +
				"\\newcommand{\\RaptorpreStartEvents}{" + preStartEventCount + "}\n" +
				"\\newcommand{\\RaptorpostJoinEvents}{" + postJoinEventCount + "}\n" + 
				"\\newcommand{\\RaptorstaticInitEvents}{" + staticEventCount + "}\n");
	}
	
	public void printLocksetObjectCounts() {
		Util.log("\\newcommand{\\RaptorWriteLSCreation}{" + writeLocksetCreation + "}\n" +
				"\\newcommand{\\RaptorWriteLSRemoved}{" + writeLocksetRemoved + "}\n" +
				"\\newcommand{\\RaptorWriteLSDestroyed}{" + writeLocksetDestroyed + "}\n" +
				"\\newcommand{\\RaptorReadLSCreation}{" + readLocksetCreation + "}\n" +
				"\\newcommand{\\RaptorReadLSRemoved}{" + readLocksetRemoved + "}\n" +
				"\\newcommand{\\RaptorReadLSDestroyed}{" + readLocksetDestroyed + "}\n" +
				"\\newcommand{\\RaptorLockLSCreation}{" + lockLocksetCreation + "}\n" +
				"\\newcommand{\\RaptorLockLSRemoved}{" + lockLocksetRemoved + "}\n" +
				"\\newcommand{\\RaptorLockLSDestroyed}{" + lockLocksetDestroyed + "}\n" +
				"\\newcommand{\\RaptorArtifLockLSCreation}{" + artificialLockLocksetCreation + "}\n" +
				"\\newcommand{\\RaptorArtifLockLSDestroyed}{" + artificialLockLocksetDestroyed + "}\n");
		Util.log("=== Average LS processed at Acquire: " + (acquireEventCount==0?0:(lastAcquireEvent/acquireEventCount)) + " ===");
		Util.log("=== Average LS processed at Release: " + (releaseEventCount==0?0:(lastReleaseEvent/releaseEventCount)) + " ===");
		Util.log("=== Average LS processed at PreRelease: " + (releaseEventCount==0?0:(lastPreReleaseEvent/releaseEventCount)) + " ===");
		Util.log("=== Average LS processed at Write: " + (writeEventCount==0?0:(lastWriteEvent/writeEventCount)) + " ===");
		Util.log("=== Average LS processed at Read: " + (readEventCount==0?0:(lastReadEvent/readEventCount)) + " ===");
		Util.log("=== Total time to process Acquire: " + timeAcquireEvent/nano + " ===");
		Util.log("=== Total time to process Release: " + timeReleaseEvent/nano + " ===");
		Util.log("=== Total time to process PreRelease: " + timePreReleaseEvent/nano + " ===");
		Util.log("=== Total time to process Write: " + timeWriteEvent/nano + " ===");
		Util.log("=== Total time to process Read: " + timeReadEvent/nano + " ===");
		Util.log("=== Latest time to process Acquire: " + lastTimeAcquireEvent/nano + " ===");
		Util.log("=== Latest time to process Release: " + lastTimeReleaseEvent/nano + " ===");
		Util.log("=== Latest time to process PreRelease: " + lastTimePreReleaseEvent/nano + " ===");
		Util.log("=== Latest time to process Write: " + lastTimeWriteEvent/nano + " ===");
		Util.log("=== Latest time to process Read: " + lastTimeReadEvent/nano + " ===");
		Util.log("=== Average time to process Acquire: " + (acquireEventCount==0?0:(timeAcquireEvent/acquireEventCount)/nano) + " ===");
		Util.log("=== Average time to process Release: " + (releaseEventCount==0?0:(timeReleaseEvent/releaseEventCount)/nano) + " ===");
		Util.log("=== Average time to process PreRelease: " + (releaseEventCount==0?0:(timePreReleaseEvent/releaseEventCount)/nano) + " ===");
		Util.log("=== Average time to process Write: " + (writeEventCount==0?0:(timeWriteEvent/writeEventCount)/nano) + " ===");
		Util.log("=== Average time to process Read: " + (readEventCount==0?0:(timeReadEvent/readEventCount)/nano) + " ===");
		Util.log("=== Total time to process all Event Types: "+  (timeAcquireEvent+timeReleaseEvent+timePreReleaseEvent+timeWriteEvent+timeReadEvent)/nano + " ===");
//		synchronized(GCPTool.eventLock) {
//			Util.log("Current Variable Locksets");
//			for (LocksetVar var : LocksetVar.locksetVars) {
//				var.printLockSet(GCPEvents.out, true);
//			}
//			Util.log("Current Lock Locksets");
//			for (LocksetLock lock : LocksetLock.locksetLocks) {
//				if (lock != null)
//					lock.printLockSet(GCPEvents.out, true);
//			}
//		}
	}
	
	static class periodicEvents extends PeriodicTaskStmt {
		
		private long lastTime;
		private long initTime;
		private long periodicLSUpdate = 0;
		private long priorTotalEvents = 0;

		public periodicEvents(String name, long interval) {
			super(name, interval);
			lastTime = System.currentTimeMillis();
			initTime = lastTime;
		}

		@Override
		public void run() throws Exception {
				Util.log("=== Event Total: " + totalEventOrder + " ===");
				Util.log("=== Event Diff: " + (totalEventOrder-priorTotalEvents) + " ===");
				long time = System.currentTimeMillis();
				Util.log("=== Event TotalTime: " + (time - initTime) + " ===");
				Util.log("=== Event Time: " + (time - lastTime) + " ===");
				lastTime = time;
				periodicLSUpdate += (time - lastTime);
				priorTotalEvents = totalEventOrder;
				Util.log("=== Read Events: " + readEventCount + " ===");
				Util.log("=== Write Events: " + writeEventCount + " ===");
				Util.log("=== Acquire Events: " + acquireEventCount + " ===");
				Util.log("=== Release Events: " + releaseEventCount + " ===");
				Util.log("=== Artif Write Events: " + artificialWriteEventCount + " ===");
				Util.log("=== Artif Acquire Events: " + artificialAcquireEventCount + " ===");
				Util.log("=== Artif Release Events: " + artificialReleaseEventCount + " ===");
				Util.log("=== volatile Events: " + volatileEventCount + " ===");
				Util.log("=== preStart Events: " + preStartEventCount + " ===");
				Util.log("=== postJoin Events: " + postJoinEventCount + " ===");
				Util.log("=== static Events: " + staticEventCount + " ===");
				Util.log("=== Write LS Created: " + writeLocksetCreation + " ===");
				Util.log("=== Write LS Removed: " + writeLocksetRemoved + " ===");
				Util.log("=== Write LS Destroyed: " + writeLocksetDestroyed + " ===");
				Util.log("=== Read LS Created: " + readLocksetCreation + " ===");
				Util.log("=== Read LS Removed: " + readLocksetRemoved + " ===");
				Util.log("=== Read LS Destroyed: " + readLocksetDestroyed + " ===");
				Util.log("=== Lock LS Created: " + lockLocksetCreation + " ===");
				Util.log("=== Lock LS Removed: " + lockLocksetRemoved + " ===");
				Util.log("=== Lock LS Destroyed: " + lockLocksetDestroyed + " ===");
				Util.log("=== Artificial Lock LS Created: " + artificialLockLocksetCreation + " ===");
				Util.log("=== Artificial Lock LS Destroyed: " + artificialLockLocksetDestroyed + " ===");
				Util.log("=== Average LS processed at Acquire: " + (acquireEventCount==0?0:(lastAcquireEvent/acquireEventCount)) + " ===");
				Util.log("=== LS processed at last Acquire: " + (acquireEventCount==0?0:latestAcquireEvent) + "===");
				Util.log("=== Average LS processed at Release: " + (releaseEventCount==0?0:(lastReleaseEvent/releaseEventCount)) + " ===");
				Util.log("=== Average LS processed at PreRelease: " + (releaseEventCount==0?0:(lastPreReleaseEvent/releaseEventCount)) + " ===");
				Util.log("=== Average LS processed at Write: " + (writeEventCount==0?0:(lastWriteEvent/writeEventCount)) + " ===");
				Util.log("=== Average LS processed at Read: " + (readEventCount==0?0:(lastReadEvent/readEventCount)) + " ===");
				Util.log("=== Total time to process Acquire: " + timeAcquireEvent/nano + " ===");
				Util.log("=== Total time to process Release: " + timeReleaseEvent/nano + " ===");
				Util.log("=== Total time to process PreRelease: " + timePreReleaseEvent/nano + " ===");
				Util.log("=== Total time to process Write: " + timeWriteEvent/nano + " ===");
				Util.log("=== Total time to process Read: " + timeReadEvent/nano + " ===");
				Util.log("=== Latest time to process Acquire: " + lastTimeAcquireEvent/nano + " ===");
				Util.log("=== Latest time to process Release: " + lastTimeReleaseEvent/nano + " ===");
				Util.log("=== Latest time to process PreRelease: " + lastTimePreReleaseEvent/nano + " ===");
				Util.log("=== Latest time to process Write: " + lastTimeWriteEvent/nano + " ===");
				Util.log("=== Latest time to process Read: " + lastTimeReadEvent/nano + " ===");
				Util.log("=== Average time to process Acquire: " + (acquireEventCount==0?0:(timeAcquireEvent/acquireEventCount)/nano) + " ===");
				Util.log("=== Average time to process Release: " + (releaseEventCount==0?0:(timeReleaseEvent/releaseEventCount)/nano) + " ===");
				Util.log("=== Average time to process PreRelease: " + (releaseEventCount==0?0:(timePreReleaseEvent/releaseEventCount)/nano) + " ===");
				Util.log("=== Average time to process Write: " + (writeEventCount==0?0:(timeWriteEvent/writeEventCount)/nano) + " ===");
				Util.log("=== Average time to process Read: " + (readEventCount==0?0:(timeReadEvent/readEventCount)/nano) + " ===");
				Util.log("=== Total time to process all Event Types: "+  (timeAcquireEvent+timeReleaseEvent+timePreReleaseEvent+timeWriteEvent+timeReadEvent)/nano + " ===");
				synchronized(GCPTool.eventLock) {
					long writeLocksetSize = 0;
					long writeLocksetCount = 0;
					long readLocksetSize = 0;
					long readLocksetCount = 0;
					long lockLocksetSize = 0;
					long lockLocksetCount = 0;
					for (LocksetVar var : LocksetVar.locksetVars) {
						if (var instanceof WriteVar) {
							writeLocksetSize += var.lockset.locks.size();
							writeLocksetSize += var.lockset.threads.size();
							for (Lock ccpLock : var.lockset.PCPlocks.keySet()) {
								writeLocksetSize += var.lockset.PCPlocks.get(ccpLock).PCPlocks.size();
								writeLocksetSize += var.lockset.PCPlocks.get(ccpLock).PCPthreads.size();
								if (var.lockset.PCPlocks.get(ccpLock).CCPWriteXiThread != null) writeLocksetSize++;
								writeLocksetSize += var.lockset.PCPlocks.get(ccpLock).CCPReadXiThreads.size();
							}
							writeLocksetCount++;
						} else {
							readLocksetSize += var.lockset.locks.size();
							readLocksetSize += var.lockset.threads.size();
							for (Lock ccpLock : var.lockset.PCPlocks.keySet()) {
								readLocksetSize += var.lockset.PCPlocks.get(ccpLock).PCPlocks.size();
								readLocksetSize += var.lockset.PCPlocks.get(ccpLock).PCPthreads.size();
								if (var.lockset.PCPlocks.get(ccpLock).CCPWriteXiThread != null) readLocksetSize++;
								readLocksetSize += var.lockset.PCPlocks.get(ccpLock).CCPReadXiThreads.size();
							}
							readLocksetCount++;
						}
					}
					for (LocksetLock lock : LocksetLock.locksetLocks) {
						lockLocksetSize += lock.lockset.locks.size();
						lockLocksetSize += lock.lockset.threads.size();
						for (Lock ccpLock : lock.lockset.PCPlocks.keySet()) {
							lockLocksetSize += lock.lockset.PCPlocks.get(ccpLock).PCPlocks.size();
							lockLocksetSize += lock.lockset.PCPlocks.get(ccpLock).PCPthreads.size();
							if (GCPTool.Verbose == 1) assert lock.lockset.PCPlocks.get(ccpLock).CCPReadXiThreads.isEmpty();
							if (GCPTool.Verbose == 1) assert lock.lockset.PCPlocks.get(ccpLock).CCPWriteXiThread == null;
						}
						lockLocksetCount++;
					}
					Util.log("=== Average Size of Write Locksets: " + (writeLocksetCount==0?0:(writeLocksetSize/writeLocksetCount) + " ==="));
					Util.log("=== Average Size of Read Locksets: " + (readLocksetCount==0?0:(readLocksetSize/readLocksetCount) + " ==="));
					Util.log("=== Average Size of Lock Locksets: " + (lockLocksetCount==0?0:(lockLocksetSize/lockLocksetCount) + " ==="));
				}
				if (periodicLSUpdate >= 900000) { //15 min
					synchronized(GCPTool.eventLock){
						Util.log("Current Variable Locksets");
						for (LocksetVar var : LocksetVar.locksetVars) {
//							Util.log("class for var: " + ((Var)var).getEvent().getTarget().getClass());
//							Util.log("class var loc: " + ((Var)var).getEvent().getAccessInfo().getLoc());
							var.printLockSet(GCPEvents.out, true);
							
						}
						Util.log("Current Lock Locksets");
						for (LocksetLock lock : LocksetLock.locksetLocks) {
							if (lock != null)
								lock.printLockSet(GCPEvents.out, true);
						}
						periodicLSUpdate = 0;
					}
				}
		}
	}
	
	public <T extends Event> void trackEventOrder (T event) {
		totalEventOrder++;
		if (event instanceof AcquireEvent) {
			acquireEventCount++;
		} else if (event instanceof ReleaseEvent) {
			releaseEventCount++;
		} else if (event instanceof AccessEvent) {
			if (((AccessEvent)event).isWrite()) {
				writeEventCount++;
			} else {
				readEventCount++;
			}
		} else if (event instanceof VolatileAccessEvent) {
			acquireEventCount++;
			artificialAcquireEventCount++; 
			if (((VolatileAccessEvent)event).isWrite()) {
				writeEventCount++;
			} else {
				readEventCount++;
			}
			releaseEventCount++;
			artificialReleaseEventCount++;
			volatileEventCount++;
			totalEventOrder++;
		} else if (event instanceof StartEvent) {
			if (RR.raptorHBModeOption.get()) {
				acquireEventCount+=2;
				artificialAcquireEventCount+=2;
				releaseEventCount+=2;
				artificialReleaseEventCount+=2;
				totalEventOrder+=3;
			} else {
				acquireEventCount+=2;
				artificialAcquireEventCount+=2;
				writeEventCount+=3;
				artificialWriteEventCount+=3;
				releaseEventCount+=2;
				artificialReleaseEventCount+=2;
				totalEventOrder+=3;
			}
			preStartEventCount++;
		} else if (event instanceof JoinEvent) {
			if (RR.raptorHBModeOption.get()) {
				acquireEventCount+=2;
				artificialAcquireEventCount+=2;
				releaseEventCount+=2;
				artificialReleaseEventCount+=2;
				totalEventOrder+=3;
			} else {
				acquireEventCount+=2;
				artificialAcquireEventCount+=2;
				writeEventCount+=3;
				artificialWriteEventCount+=3;
				releaseEventCount+=2;
				artificialReleaseEventCount+=2;
				totalEventOrder+=3;
			}
			postJoinEventCount++;
		}
	}
}

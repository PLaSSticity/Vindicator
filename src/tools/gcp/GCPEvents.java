package tools.gcp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import rr.event.AccessEvent;
import rr.event.Event;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.tool.RR;
import acme.util.Util;

public class GCPEvents {
	
	static BufferedWriter out;
	static int eventCounter = 0;
	static final int limit = RR.raptorSetDatalogEventLimitOption.get(); //Default: ignores limit
	static HashMap<ShadowLock, ShadowThread> currentlyHeldLocks = new HashMap<ShadowLock, ShadowThread>();
	static int eventIteration = 0;
	static BufferedWriter traceAcquire;
	static BufferedWriter traceRelease;
	static BufferedWriter traceRead;
	static BufferedWriter traceWrite;
	static BufferedWriter traceThread;
	static BufferedWriter traceEvent;
	
	static final boolean printEvent = RR.printEventOption.get();
	static final boolean printLatexTrace = RR.raptorPrintLatexOption.get();
	static final boolean printDatalogTrace = RR.raptorPrintDatalogOption.get();
	static final boolean printDatalogTraceConcurrently = RR.raptorPrintDatalogConcurrentlyOption.get();
	 
	static void acquire(ShadowThread thr, Lock lock, ShadowLock sl) {
		if (printDatalogTrace && !printDatalogTraceConcurrently) {
			verifyTrace(eventCounter++);
			updateTrace(traceAcquire, eventCounter, lock.getLockID(), thr.getTid());
			if (limit != -1) {
				currentlyHeldLocks.put(sl, thr);
			}
		} else {
			if (printDatalogTraceConcurrently) {
				verifyTrace(eventCounter++);
				updateTrace(traceAcquire, eventCounter, lock.getLockID(), thr.getTid());
				currentlyHeldLocks.put(sl, thr);
			}
			if (printEvent) Util.message("Event: " + eventCounter + "T" + thr.getTid() + ": acq " + lock + " lock ver: " + (lock.latestLock==null?lock.getVersion()+1:lock.latestLock.getVersion()+1));
			if (printLatexTrace) updateLatex(thr, "\\Acquire{" + lock.name + "}{" + (lock.latestVersion+1) + "}");
			long start_time = System.nanoTime();
			GCPAcquire.acquire(thr, lock, sl);
			if (GCPTool.PrintStats) GCPStats.lastTimeAcquireEvent = System.nanoTime() - start_time;
			if (GCPTool.PrintStats) GCPStats.timeAcquireEvent += GCPStats.lastTimeAcquireEvent;
		}
	}
	
	static void release(ShadowThread thr, Lock lock, ShadowLock sl) {
		if (printDatalogTrace && !printDatalogTraceConcurrently) {
			verifyTrace(eventCounter++);
			updateTrace(traceRelease, eventCounter, lock.getLockID(), thr.getTid());
			if (limit != -1) {
				currentlyHeldLocks.remove(sl);
			}
		} else {
			if (printDatalogTraceConcurrently) {
				verifyTrace(eventCounter++);
				updateTrace(traceRelease, eventCounter, lock.getLockID(), thr.getTid());
				currentlyHeldLocks.remove(sl);
			}
			if (printEvent) Util.message("Event: " + eventCounter + "T" + thr.getTid() + ": Pre rel " + lock + " lock ver: " + (lock.latestLock==null?lock.getVersion():lock.latestLock.getVersion()));
			if (printLatexTrace) updateLatex(thr, "\\Release{" + lock.name + "}{" + lock.latestVersion + "}");
			long start_time = System.nanoTime();
			GCPPreRelease.preRelease(thr, lock);
			if (GCPTool.PrintStats) GCPStats.lastTimePreReleaseEvent = System.nanoTime() - start_time;
			if (GCPTool.PrintStats) GCPStats.timePreReleaseEvent += GCPStats.lastTimePreReleaseEvent;
			if (printEvent) Util.message("T" + thr.getTid() + ": rel " + lock + " lock ver: " + (lock.latestLock==null?lock.getVersion():lock.latestLock.getVersion()));
			start_time = System.nanoTime();
			GCPRelease.release(thr, lock);
			if (GCPTool.PrintStats) GCPStats.lastTimeReleaseEvent = System.nanoTime() - start_time;
			if (GCPTool.PrintStats) GCPStats.timeReleaseEvent += GCPStats.lastTimeReleaseEvent;
		}
	}
	
	static WriteVar wri(ShadowThread thr, Var var, Event event, long eventOrder) {
		if (printDatalogTrace && !printDatalogTraceConcurrently) {
			verifyTrace(eventCounter++);
			updateTrace(traceWrite, eventCounter, var.getEvent().hashCode(), thr.getTid());
		} else {
			if (printDatalogTraceConcurrently) {
				verifyTrace(eventCounter++);
				updateTrace(traceWrite, eventCounter, var.getEvent().hashCode(), thr.getTid());	
			}
			if (printEvent) Util.message("Event: " + eventCounter + "T" + thr.getTid() + ": wr " + var);
			if (printLatexTrace) updateLatex(thr, "\\Write{" + var +"}{" + (var.latestInstance!=null?var.latestInstance.version+1:var.version+1) + "}");
			long start_time = System.nanoTime();
			WriteVar v = GCPWrite.wri(thr, var, event, eventCounter);
			if (GCPTool.PrintStats) GCPStats.lastTimeWriteEvent = System.nanoTime() - start_time;
			if (GCPTool.PrintStats) GCPStats.timeWriteEvent += GCPStats.lastTimeWriteEvent;
			return v;
		}
		return (WriteVar)var; 
	}
	
	static void rd(ShadowThread thr, Var var, Event event, long eventOrder) {
		if (printDatalogTrace && !printDatalogTraceConcurrently) {
			verifyTrace(eventCounter++);
			updateTrace(traceRead, eventCounter, var.getEvent().hashCode(), thr.getTid());
		} else {
			if (printDatalogTraceConcurrently) {
				verifyTrace(eventCounter++);
				updateTrace(traceRead, eventCounter, var.getEvent().hashCode(), thr.getTid());
			}
			if (printEvent) Util.message("Event: " + eventCounter + "T" + thr.getTid() + ": rd " + var);
			if (printLatexTrace) updateLatex(thr, "\\Read{" + var + "}{" + (var.latestInstance!=null?var.latestInstance.version+1:var.version+1) + "}");
			long start_time = System.nanoTime();
			GCPRead.rd(thr, var, event, eventCounter);
			if (GCPTool.PrintStats) GCPStats.lastTimeReadEvent = System.nanoTime() - start_time;
			if (GCPTool.PrintStats) GCPStats.timeReadEvent += GCPStats.lastTimeReadEvent;
		}
	}
	
	//write-write, read-write races
	static void reportWrRace(Var x_i) {
		if (x_i.lockset.uniqueWriteThrModifier.type != Type.HB){
			GCPTool.error(x_i, null, "HB");
		} else {
			GCPTool.error(x_i, null, "CP");
		}
		x_i.lockset.setUniqueWriteThr(x_i.lockset.uniqueWriteThr, Type.CP, 0);
	}
	
	//write-read races
	static void reportRdRace(Var x_i, ShadowThread x_i_T) {
		if (GCPTool.Verbose == 1) assert x_i_T != null;
		if (x_i.lockset.uniqueReadThr.get(x_i_T).type != Type.HB){
			GCPTool.error(x_i, x_i_T, "HB");
		} else {
			GCPTool.error(x_i, x_i_T, "CP");
		}
		x_i.lockset.setUniqueReadThr(x_i_T, Type.CP, 0);
	}
	
	static void checkRaces() {
		for (LocksetVar rho_var : LocksetVar.locksetVars) {
			if (rho_var instanceof ReadVar) { 
				ReadVar read_var = (ReadVar) rho_var;
				//check for null since this read may not be succeeded by a write
				if (read_var.lockset.uniqueWriteThr != null && read_var.lockset.uniqueWriteThrModifier.type != Type.CP) {
					if (read_var.lockset.uniqueWriteThrModifier.type != Type.HB) {
						GCPTool.error(read_var, null, "HB");
					} else {
						GCPTool.error(read_var, null, "CP");
					}
				}
			} else if (rho_var instanceof WriteVar) {
				WriteVar write_var = (WriteVar) rho_var;
				//check for null since this write may not be succeeded by a write
				if (write_var.lockset.uniqueWriteThr != null && write_var.lockset.uniqueWriteThrModifier.type != Type.CP) {
					if (write_var.lockset.uniqueWriteThrModifier.type != Type.HB) {
						GCPTool.error(write_var, null, "HB");
					} else {
						GCPTool.error(write_var, null, "CP");
					}
				}
				//check for empty since this write may not be succeeded by a read
				if (!write_var.lockset.uniqueReadThr.isEmpty()) {
					for (ShadowThread xi_Thr : write_var.lockset.uniqueReadThr.keySet()) {
						if (write_var.lastAccessedByThread == null && GCPTool.Verbose == 1) assert write_var.version == 0;
						if (write_var.lastAccessedByThread != null && !write_var.lastAccessedByThread.equals(xi_Thr)) {
							if (GCPTool.Verbose == 1) assert xi_Thr != null;
							if (write_var.lockset.uniqueReadThr.get(xi_Thr).type != Type.CP) {
								if (write_var.lockset.uniqueReadThr.get(xi_Thr).type != Type.HB) {
									GCPTool.error(write_var, xi_Thr, "HB");
								} else {
									GCPTool.error(write_var, xi_Thr, "CP");
								}
							}
						}
					}
				}
			}
		}
	}
	
	static void initializeLatex() {
		if (printLatexTrace) {
			try{
				out = new BufferedWriter(new FileWriter("Example.tex"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	static void initializeTraceWriters(String outputDir) {
		if (printDatalogTrace || printDatalogTraceConcurrently) {
			try {
				new File(outputDir).mkdirs();
				traceAcquire = new BufferedWriter(new FileWriter(outputDir+"AcqEvent.input"));
				traceRelease = new BufferedWriter(new FileWriter(outputDir+"RelEvent.input"));
				traceRead = new BufferedWriter(new FileWriter(outputDir+"ReadEvent.input"));
				traceWrite = new BufferedWriter(new FileWriter(outputDir+"WriteEvent.input"));
				traceThread = new BufferedWriter(new FileWriter(outputDir+"ThreadForEvent.input"));
				traceEvent = new BufferedWriter(new FileWriter(outputDir+"Event.input"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	static void updateLatex(ShadowThread thr, String command) {
		try {
			if (eventCounter == 0) {
				String tableHeader = "\\begin{figure*}\n\\centering\n\\begin{tabular}{";
				String threadCount = "";
				for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
					tableHeader += "l";
					threadCount += "Thread " + (i+1) + " & "; 
				}
				tableHeader += "l}\n" + threadCount + "Lockset After";
				out.write(tableHeader);
				out.write("\\\\\\hline\n");
				eventCounter++;
			}
			for (int i = 1; i < thr.getTid(); i++) {
				out.write("&");
			} 
			out.write(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void verifyTrace(int eventOrder) {
		if (eventIteration == 0) {
			eventIteration++; 
			initializeTraceWriters("datalog_output/events_"+eventIteration+"/");
		} else if (limit != -1 && eventOrder >= limit*eventIteration) {
			eventOrder = closeTraceWriters(eventOrder);
			initializeTraceWriters("datalog_output/events_"+eventIteration+"/");
			for (ShadowLock lock : currentlyHeldLocks.keySet()) {
				eventOrder++;
				updateTrace(traceAcquire, eventOrder, GCPTool.gcpLock.get(lock).getLockID(), currentlyHeldLocks.get(lock).getTid());
			}
		}
	}
	
	public static void updateTrace(BufferedWriter traceOut, int eventOrder, int address, int thread) {
		try {
			traceEvent.write(eventOrder + "\n");
			traceThread.write(eventOrder + "|" + thread + "\n");
			traceOut.write(eventOrder + "|" + address + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static void closeLatex() {
		if (printLatexTrace) {
			try {
				out.write("\n\\end{tabular}\n");
				out.write("\\end{figure*}\n");
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	static int closeTraceWriters(int eventOrder) {
		if (printDatalogTrace || printDatalogTraceConcurrently) {
			try {
				if (!currentlyHeldLocks.isEmpty()) {
					for (ShadowLock lock : currentlyHeldLocks.keySet()) {
						eventOrder++;
						updateTrace(traceRelease, eventOrder, GCPTool.gcpLock.get(lock).getLockID(), currentlyHeldLocks.get(lock).getTid());
					}
					eventIteration++;
				}
				traceAcquire.close();
				traceRelease.close();
				traceRead.close();
				traceWrite.close();
				traceThread.close();
				traceEvent.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return eventOrder;
	}
}

enum Type implements Comparable<Type> {
	HB, LS, CP, PCP;
}

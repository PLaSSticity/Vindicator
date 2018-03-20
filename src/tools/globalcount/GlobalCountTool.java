package tools.globalcount;

import java.util.concurrent.Callable;

import acme.util.Util;
import acme.util.option.CommandLine;
import rr.annotations.Abbrev;
import rr.event.AccessEvent;
import rr.event.AcquireEvent;
import rr.event.ClassAccessedEvent;
import rr.event.ClassInitializedEvent;
import rr.event.JoinEvent;
import rr.event.MethodEvent;
import rr.event.ReleaseEvent;
import rr.event.StartEvent;
import rr.event.VolatileAccessEvent;
import rr.event.WaitEvent;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.tool.Tool;

@Abbrev("GlobalCount")
public class GlobalCountTool extends Tool {
	
	static long event_count = 0;
	static long event_exit = 0; //If BUILD_EVENT_GRAPH is true, event_count should add event_exit again
	static long event_fake_fork = 0; //Will implement
	static long event_acquire = 0;
	static long event_release = 0;
	static long event_write = 0;
	static long event_read = 0;
	static long event_volatile_write = 0;
	static long event_volatile_read = 0;
	static long event_start = 0;
	static long event_join = 0;
	static long event_preWait = 0;
	static long event_postWait = 0;
	static long event_class_init = 0;
	static long event_class_accessed = 0;
	static final Object event_lock = new Object();

	@Override
	public String toString() {
		return "Empty";
	}
	
	public GlobalCountTool(String name, Tool next, CommandLine commandLine) {
		super(name, next, commandLine);
	}
	
	@Override
	public ShadowVar makeShadowVar(AccessEvent fae) {
		return fae.getThread();
	}
	
	@Override
	public void exit(MethodEvent me) {
		ShadowThread td = me.getThread();
		if (td.getParent() == null && td.getTid() != 0 /*not the main thread*/ && !td.getThread().getName().equals("Finalizer")) {
			String methodName = me.getInfo().getName();
			Object target = me.getTarget();
			if ((methodName.equals("call") && target instanceof Callable) ||
			    (methodName.equals("run")  && target instanceof Runnable)) {
				synchronized(event_lock) {
					event_count++;
					event_exit++;
				}
			}
		}
	}
	
	@Override
	public void fini() {
		Util.log("event total: " + event_count);
		Util.log("event exit: " + event_exit);
		Util.log("event fake fork: " + event_fake_fork);
		Util.log("event acquire: " + event_acquire);
		Util.log("event release: " + event_release);
		Util.log("event write: " + event_write);
		Util.log("event read: " + event_read);
		Util.log("event volatile write: " + event_volatile_write);
		Util.log("event volatile read: " + event_volatile_read);
		Util.log("event start: " + event_start);
		Util.log("event join: " + event_join);
		Util.log("event pre wait: " + event_preWait);
		Util.log("event post wait: " + event_postWait);
		Util.log("event class init: " + event_class_init);
		Util.log("event class accessed: " + event_class_accessed);
	}
	
	@Override
	public void acquire(AcquireEvent fae) {
		synchronized(event_lock) {
			event_count++;
			event_acquire++;
		}
		super.acquire(fae);
	}
	
	@Override
	public void release(ReleaseEvent fae) {
		synchronized(event_lock) {
			event_count++;
			event_release++;
		}
		super.release(fae);
	}
	
	@Override
	public void access(final AccessEvent fae) {
		synchronized(event_lock) {
			event_count++;
			if (fae.isWrite()) {
				event_write++;
			} else {
				event_read++;
			}
		}
		super.access(fae);
	}
	
	@Override
	public void volatileAccess(final VolatileAccessEvent fae) {
		synchronized(event_lock) {
			event_count++;
			if (fae.isWrite()) {
				event_volatile_write++;
			} else {
				event_volatile_read++;
			}
		}
		super.volatileAccess(fae);
	}
	
	@Override
	public void preStart(final StartEvent se) {
		synchronized(event_lock) {
			event_count++;
			event_start++;
		}
		super.preStart(se);
	}
	
	@Override
	public void postJoin(final JoinEvent je) {
		synchronized(event_lock) {
			event_count++;
			event_join++;
		}
		super.postJoin(je);
	}
	
	@Override
	public void preWait(WaitEvent we) {
		synchronized(event_lock) {
			event_count++;
			event_preWait++;
		}
		super.preWait(we);
	}
	
	@Override
	public void postWait(WaitEvent we) {
		synchronized(event_lock) {
			event_count++;
			event_postWait++;
		}
		super.postWait(we);
	}
	
	@Override
	public void classInitialized(ClassInitializedEvent e) {
		synchronized(event_lock) {
			event_count++;
			event_class_init++;
		}
		super.classInitialized(e);
	}
	
	@Override
	public void classAccessed(ClassAccessedEvent e) {
		synchronized(event_lock) {
			event_count++;
			event_class_accessed++;
		}
		super.classAccessed(e);
	}
}

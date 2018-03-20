package tools.wdc;

import java.util.ArrayDeque;
import java.util.HashMap;

import rr.state.ShadowThread;

public class PerThreadQueue<T> {

	final HashMap<ShadowThread,ArrayDeque<T>> threadToQueueMap;
	
	public PerThreadQueue() {
		threadToQueueMap = new HashMap<ShadowThread,ArrayDeque<T>>();
	}
	
	public void addLast(ShadowThread td, T element) {
		ArrayDeque<T> queue = getQueue(td);
		queue.addLast(element);
	}
	
	public T peekLast(ShadowThread td) {
		ArrayDeque<T> queue = getQueue(td);
		return queue.peekLast();
	}

	public boolean isEmpty(ShadowThread td) {
		ArrayDeque<T> queue = getQueue(td);
		return queue.isEmpty();
	}
	
	public T peekFirst(ShadowThread td) {
		ArrayDeque<T> queue = getQueue(td);
		return queue.peekFirst();
	}
	
	public T removeFirst(ShadowThread td) {
		ArrayDeque<T> queue = getQueue(td);
		return queue.removeFirst();
	}
	
	ArrayDeque<T> getQueue(ShadowThread td) {
		ArrayDeque<T> queue = threadToQueueMap.get(td);
		if (queue == null) {
			queue = new ArrayDeque<T>();
			threadToQueueMap.put(td, queue);
		}
		return queue;
	}
	
	@Override
	public PerThreadQueue<T> clone() {
		PerThreadQueue<T> ptQueue = new PerThreadQueue<T>();
		for (ShadowThread td : threadToQueueMap.keySet()) {
			ptQueue.threadToQueueMap.put(td, threadToQueueMap.get(td).clone());
		}
		return ptQueue;
	}
}

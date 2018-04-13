package tools.wdc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Random;


import acme.util.Assert;
import acme.util.Util;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.tool.RR;

public class EventNode implements Iterable<EventNode> {

	long eventNumber;
	final AcqRelNode surroundingCriticalSection;
	final int threadID; // TODO: change to ShadowThread? Also, could be combined with surroundingCriticalSection; only AcqRelNode needs a threadID then
	static final boolean VERBOSE_GRAPH = false;

	Iterable<EventNode> sinkOrSinks = EMPTY_NODES;
	Iterable<EventNode> sourceOrSources = EMPTY_NODES;

	static final Iterable<EventNode> EMPTY_NODES =
		new Iterable<EventNode>() {
			@Override
			public Iterator<EventNode> iterator() {
				return Collections.emptyIterator();
			}
		};

	int myLatestTraversal;

	static int nextTraversal = 1;
	static HashMap<Integer,EventNode> threadToFirstEventMap = new HashMap<Integer,EventNode>();

	/* Adds example numbers and node labels to events */
	public static final boolean DEBUG_EXNUM_LABEL = false;
	/* Adds field names and some extra debug information to read/write events */
	static final boolean DEBUG_ACCESS_INFO = false;

	static final HashMap<EventNode,Long> exampleNumberMap;
	static final HashMap<EventNode,String> nodeLabelMap;

	static {
		if (DEBUG_EXNUM_LABEL) {
			exampleNumberMap = new HashMap<EventNode,Long>();
			nodeLabelMap = new HashMap<EventNode,String>();
		} else {
			exampleNumberMap = null;
			nodeLabelMap = null;
		}
	}

	public EventNode(long eventNumber, long exampleNumber, int threadID, AcqRelNode currentCriticalSection) {
		this(eventNumber, exampleNumber, threadID, currentCriticalSection, "");
	}
	
	public EventNode(long eventNumber, long exampleNumber, int threadId, AcqRelNode currentCriticalSection, String nodeLabel) {
		this.eventNumber = eventNumber;
		this.surroundingCriticalSection = currentCriticalSection;
		this.threadID = threadId;
		
		if (DEBUG_EXNUM_LABEL) {
			exampleNumberMap.put(this, exampleNumber);
			nodeLabelMap.put(this, nodeLabel);
		}
	}

	public static boolean edgeExists(EventNode sourceNode, EventNode sinkNode) {
		boolean exists = containsNode(sourceNode.sinkOrSinks, sinkNode);
		if (VERBOSE_GRAPH) Assert.assertTrue(containsNode(sinkNode.sourceOrSources, sourceNode) == exists);
		return exists;
	}
	
	public static void addEdge(EventNode sourceNode, EventNode sinkNode) {
		if (VERBOSE_GRAPH) Assert.assertTrue(sourceNode != sinkNode);
		synchronized(sourceNode) {
			sourceNode.sinkOrSinks = addNode(sourceNode.sinkOrSinks, sinkNode);
		}
		sinkNode.sourceOrSources = addNode(sinkNode.sourceOrSources, sourceNode);
		//Only update sinkNode's eventNumber if it has no sources (as in, before fini() is called)
		if (sinkNode.sinkOrSinks.equals(EMPTY_NODES) && sourceNode.eventNumber >= sinkNode.eventNumber) {
			sinkNode.eventNumber = sourceNode.eventNumber + 1;
			if (VERBOSE_GRAPH) addEventToThreadToItsFirstEventsMap(sinkNode);
		} else {
			if (VERBOSE_GRAPH) Assert.assertTrue(sinkNode.eventNumber >= 0);
//			Util.log("sinkNode has sinks.");
		}
	}
	
	static Iterable<EventNode> addNode(Iterable<EventNode> nodeOrNodes, EventNode newNode) {
		if (VERBOSE_GRAPH) Assert.assertTrue(!containsNode(nodeOrNodes, newNode));
		if (nodeOrNodes == EMPTY_NODES) {
			return newNode;
		} else if (nodeOrNodes instanceof EventNode) {
			LinkedList<EventNode> nodes = new LinkedList<EventNode>();
			nodes.add((EventNode)nodeOrNodes);
			nodes.add(newNode);
			return nodes;
		} else {
			((LinkedList<EventNode>)nodeOrNodes).add(newNode);
			return nodeOrNodes;
		}
	}
	
	public static void removeEdge(EventNode sourceNode, EventNode sinkNode) {
		synchronized(sourceNode) {
			sourceNode.sinkOrSinks = removeNode(sourceNode.sinkOrSinks, sinkNode);
		}
		sinkNode.sourceOrSources = removeNode(sinkNode.sourceOrSources, sourceNode);
	}
	
	static Iterable<EventNode> removeNode(Iterable<EventNode> nodeOrNodes, EventNode nodeToRemove) {
		if (VERBOSE_GRAPH) Assert.assertTrue(containsNode(nodeOrNodes, nodeToRemove));
		if (VERBOSE_GRAPH) Assert.assertTrue(nodeOrNodes != EMPTY_NODES);
		if (nodeOrNodes instanceof EventNode) {
			if (VERBOSE_GRAPH) Assert.assertTrue(nodeOrNodes == nodeToRemove);
			return EMPTY_NODES;
		} else {
			LinkedList<EventNode> nodes = (LinkedList<EventNode>)nodeOrNodes;
			boolean removed = ((LinkedList<EventNode>)nodeOrNodes).remove(nodeToRemove);
			if (VERBOSE_GRAPH) Assert.assertTrue(removed);
			if (nodes.size() > 1) {
				return nodes;
			} else {
				return nodes.getFirst();
			}
		}
	}

	// Support iterating over just this one EventNode
	@Override
	public Iterator<EventNode> iterator() {
		return new Iterator<EventNode>() {
			boolean nextCalled;

			@Override
			public boolean hasNext() {
				return !nextCalled;
			}

			@Override
			public EventNode next() {
				if (nextCalled) {
					throw new NoSuchElementException();
				}
				nextCalled = true;
				return EventNode.this;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	static boolean containsNode(Iterable<EventNode> nodes, EventNode nodeToFind) {
		for (EventNode node : nodes) {
			if (node == nodeToFind) {
				return true;
			}
		}
		return false;
	}

	public static int prepUniqueTraversal() {
		return prepUniqueTraversal(1);
	}

	public synchronized static int prepUniqueTraversal(int inc) {
		return nextTraversal += inc;
	}

	public static void traverse(EventNode startNode, int traversal) {
		ArrayDeque<EventNode> grayNodes = new ArrayDeque<EventNode>();
		grayNodes.addLast(startNode);
		while (!grayNodes.isEmpty()) {
			EventNode node = grayNodes.removeFirst();
			if (node.myLatestTraversal != traversal) {
				node.myLatestTraversal = traversal;
				for (EventNode sink : node.sinkOrSinks) {
					grayNodes.add(sink);
				}
			}
		}
	}

	static class Edge {
		Edge(EventNode source, EventNode sink) {
			this.source = source;
			this.sink = sink;
		}
		EventNode source;
		EventNode sink;
	}
	
	long getExampleNumber() {
		if (DEBUG_EXNUM_LABEL) {
			return exampleNumberMap.get(this);
		} else {
			return -1;
		}
	}
	
	// This implementation is imprecise in two ways:
	// (1) It adds back edges that don't necessarily reach either access
	// (2) Rather than finding acq m^j -> rel m^i paths (and adding a corresponding rel m^j -> acq m^i back edge),
	//     the implementation assumes a acq m^j -> rel m^i path exists if
	//     (a) rel m^i reaches any back edge source and (b) any back edge sink reaches acq m^j.
	public static boolean crazyNewEdges(RdWrNode firstNode, RdWrNode secondNode, boolean traverseFromAllEdges, boolean precision, File commandDir) {
		LinkedList<Edge> initialEdges = new LinkedList<Edge>();
		LinkedList<Edge> initialEdgesToRemove = new LinkedList<Edge>(); // We don't add or remove initial edges that already exist
		LinkedList<Edge> additionalBackEdges = new LinkedList<Edge>();
		LinkedList<Edge> additionalForwardEdges = new LinkedList<Edge>();
		
		// TODO: here are some sanity checks that should probably be removed later
		if (VERBOSE_GRAPH) {
			if (RR.wdcRemoveRaceEdge.get()) {
				Util.println("Validating no edge between first and second node");
				Assert.assertTrue(!edgeExists(firstNode, secondNode));
				Util.println("Validating no edge between second and first node");
				Assert.assertTrue(!edgeExists(secondNode, firstNode));
				Util.println("Validating path first to second node");
				Assert.assertTrue(!bfsTraversal(firstNode, secondNode, null, Long.MIN_VALUE, Long.MAX_VALUE, false));
				Util.println("Validating path second to first node");
				Assert.assertTrue(!bfsTraversal(secondNode, firstNode, null, Long.MIN_VALUE, Long.MAX_VALUE, false));
			}
			
			int black = prepUniqueTraversal(2);
			int gray = black - 1;
			Util.println("Cycle detection forward from start");
			Assert.assertTrue(!simplerIterativeDetectCycle(threadToFirstEventMap.get(0), true, gray, black, Long.MIN_VALUE, Long.MAX_VALUE));
			
			black = prepUniqueTraversal(2);
			gray = black - 1;
			Util.println("Cycle detection backward from first node");
			Assert.assertTrue(!simplerIterativeDetectCycle(firstNode, false, gray, black, Long.MIN_VALUE, Long.MAX_VALUE));
			
			black = prepUniqueTraversal(2);
			gray = black - 1;
			Util.println("Cycle detection backward from second node");
			Assert.assertTrue(!simplerIterativeDetectCycle(secondNode, false, gray, black, Long.MIN_VALUE, Long.MAX_VALUE));
		}
		
		// Create edges from one node's predecessors to the other node
		for (EventNode source : secondNode.sourceOrSources) {
			Edge backEdge = new Edge(source, firstNode);
			initialEdges.add(backEdge);
			if ((! edgeExists(source, firstNode)) && (! firstNode.equals(source))) { // This edge might already exist
				// TODO: is it reasonable for this edge to already exist? it's happening with aggressive merging enabled
				initialEdgesToRemove.add(backEdge);
				EventNode.addEdge(source, firstNode);
			}
		}
		for (EventNode source : firstNode.sourceOrSources) {
			Edge forwardEdge = new Edge(source, secondNode);
			initialEdges.add(forwardEdge);
			if ((!edgeExists(source, secondNode)) && (! secondNode.equals(source))) { // This edge might already exist
				initialEdgesToRemove.add(forwardEdge);
				EventNode.addEdge(source, secondNode);
			}
		}
		
		boolean addedBackEdge; // back edge added on current iteration?
		boolean addedForwardEdge; // forward edge added on current iteration?
		int iteration = 0;
		// The window of events we need to be concerned with. It can grow as we add back edges.
		long windowMin = firstNode.eventNumber;//.lastEventNumber; // everything WDC-after the first node must be totally ordered after the first node's access(es)
		long windowMax = secondNode.eventNumber; // everything WDC-before the second node must be totally ordered before the second node's access(es)
		
		do {
			
			addedBackEdge = false;
			addedForwardEdge = false;

			++iteration;
			Util.println("Iteration = " + iteration);

			LinkedList<Edge> separateInitNodes = new LinkedList<Edge>();
			for (Edge initialEdge : initialEdges) {
				separateInitNodes.add(initialEdge);
			}
			if (traverseFromAllEdges) {
				for (Edge backEdge : additionalBackEdges) {
					separateInitNodes.add(backEdge);
				}
				for (Edge forwardEdge : additionalForwardEdges) {
					separateInitNodes.add(forwardEdge);
				}
			}
			for (Edge edge : separateInitNodes) {
				// First do a reverse traversal from the second access and possibly from other edge sources
				HashMap<ShadowLock,HashMap<ShadowThread,AcqRelNode>> reachableAcqNodes = new HashMap<ShadowLock,HashMap<ShadowThread,AcqRelNode>>();
				ArrayDeque<EventNode> grayNodes = new ArrayDeque<EventNode>();
				int traversal = prepUniqueTraversal();
				grayNodes.add(edge.source);
				while (!grayNodes.isEmpty()) {
					EventNode node = grayNodes.removeFirst();
					if (node.myLatestTraversal != traversal) {
						// We don't care about nodes outside the window
						if (node.eventNumber >= windowMin) {
							// If this is an acquire, let's record it,
							// to figure out if it can reach an earlier (in total order) release of the same lock
							if (node instanceof AcqRelNode) {
								AcqRelNode acqRelNode = (AcqRelNode)node;
								if (acqRelNode.isAcquire()) {
									HashMap<ShadowThread,AcqRelNode> acqNodesForLock = reachableAcqNodes.get(acqRelNode.shadowLock);
									if (acqNodesForLock == null) {
										acqNodesForLock = new HashMap<ShadowThread,AcqRelNode>();
										reachableAcqNodes.put(acqRelNode.shadowLock, acqNodesForLock);
									}
									// We want the acq node that's latest in total order,
									// since earlier acq nodes on the same thread will be WDC ordered by definition.
									AcqRelNode currentAcqNodeForThread = acqNodesForLock.get(acqRelNode.getShadowThread());
									if (currentAcqNodeForThread == null || acqRelNode.eventNumber > currentAcqNodeForThread.eventNumber) {
										acqNodesForLock.put(acqRelNode.getShadowThread(), acqRelNode);
									}
								}
							} else if (node.surroundingCriticalSection != null) {
								AcqRelNode surroundingAcq = node.surroundingCriticalSection;
								while (surroundingAcq != null) {
									HashMap<ShadowThread,AcqRelNode> acqNodesForLock = reachableAcqNodes.get(surroundingAcq.shadowLock);
									if (acqNodesForLock == null) {
										acqNodesForLock = new HashMap<ShadowThread,AcqRelNode>();
										reachableAcqNodes.put(surroundingAcq.shadowLock, acqNodesForLock);
									}
									// We want the acq node that's latest in total order,
									// since earlier acq nodes on the same thread will be WDC ordered by definition.
									AcqRelNode currentAcqNodeForThread = acqNodesForLock.get(surroundingAcq.getShadowThread());
									if (currentAcqNodeForThread == null || surroundingAcq.eventNumber > currentAcqNodeForThread.eventNumber) {
										acqNodesForLock.put(surroundingAcq.getShadowThread(), surroundingAcq);
									}
									surroundingAcq = surroundingAcq.surroundingCriticalSection;
								}
							}
							node.myLatestTraversal = traversal;
							for (EventNode source : node.sourceOrSources) {
								grayNodes.add(source);
							}
						}
					}
				}
				
				// Second do a forward traversal from the first access and possibly from other edge sinks
				HashMap<ShadowLock,HashMap<ShadowThread,AcqRelNode>> reachableRelNodes = new HashMap<ShadowLock,HashMap<ShadowThread,AcqRelNode>>();
				traversal = prepUniqueTraversal();
				grayNodes.add(edge.sink);
				while (!grayNodes.isEmpty()) {
					EventNode node = grayNodes.removeFirst();
					if (node.myLatestTraversal != traversal) {
						// We don't care about nodes outside the window
						if (node.eventNumber <= windowMax) {
							// If this is a release, let's record it,
							// to figure out if it it's reached by a later (in total order) acquire of the same lock
							if (node instanceof AcqRelNode) {
								AcqRelNode acqRelNode = (AcqRelNode)node;
								if (!acqRelNode.isAcquire()) {
									HashMap<ShadowThread,AcqRelNode> relNodesForLock = reachableRelNodes.get(acqRelNode.shadowLock);
									if (relNodesForLock == null) {
										relNodesForLock = new HashMap<ShadowThread,AcqRelNode>();
										reachableRelNodes.put(acqRelNode.shadowLock, relNodesForLock);
									}
									// We want the rel node that's earliest in total order,
									// since later rel nodes on the same thread will be WDC ordered by definition.
									AcqRelNode currentRelNodeForThread = relNodesForLock.get(acqRelNode.getShadowThread());
									if (currentRelNodeForThread == null || acqRelNode.eventNumber < currentRelNodeForThread.eventNumber) {
										relNodesForLock.put(acqRelNode.getShadowThread(), acqRelNode);
									}
								}
							} else if (node.surroundingCriticalSection != null) {
								AcqRelNode surroundingAcq = node.surroundingCriticalSection;
								AcqRelNode surroundingRel = null;
								while (surroundingAcq != null) {
									surroundingRel = surroundingAcq.otherCriticalSectionNode;
									HashMap<ShadowThread,AcqRelNode> relNodesForLock = reachableRelNodes.get(surroundingRel.shadowLock);
									if (relNodesForLock == null) {
										relNodesForLock = new HashMap<ShadowThread,AcqRelNode>();
										reachableRelNodes.put(surroundingRel.shadowLock, relNodesForLock);
									}
									// We want the rel node that's earliest in total order,
									// since later rel nodes on the same thread will be WDC ordered by definition.
									AcqRelNode currentRelNodeForThread = relNodesForLock.get(surroundingRel.getShadowThread());
									if (currentRelNodeForThread == null || surroundingRel.eventNumber < currentRelNodeForThread.eventNumber) {
										relNodesForLock.put(surroundingRel.getShadowThread(), surroundingRel);
									}
									surroundingAcq = surroundingAcq.surroundingCriticalSection;
								}
							}
							node.myLatestTraversal = traversal;
							for (EventNode sink : node.sinkOrSinks) {
								grayNodes.add(sink);
							}
						}
					}
				}
	
				// Now check for edges that indicate a back edge w.r.t. total order
				for (ShadowLock shadowLock : reachableAcqNodes.keySet()) {
					HashMap<ShadowThread,AcqRelNode> acqNodesForLock = reachableAcqNodes.get(shadowLock);
					HashMap<ShadowThread,AcqRelNode> relNodesForLock = reachableRelNodes.get(shadowLock);
					if (relNodesForLock != null) {
						for (AcqRelNode acqNode : acqNodesForLock.values()) {
							for (AcqRelNode relNode : relNodesForLock.values()) {
								//Back Edges
								if (acqNode.eventNumber > relNode.eventNumber &&
									!containsNode(acqNode.otherCriticalSectionNode.sinkOrSinks, relNode.otherCriticalSectionNode)) {
									// Might have to look outside of current window when validating backedges
									long tempWindowMin = Math.min(windowMin, relNode.otherCriticalSectionNode.eventNumber);
									tempWindowMin = Math.min(tempWindowMin, relNode.eventNumber);
									long tempWindowMax = Math.max(windowMax, acqNode.otherCriticalSectionNode.eventNumber);
									tempWindowMax = Math.max(tempWindowMax, acqNode.eventNumber);
//									if (precision && VERBOSE_GRAPH) Assert.assertTrue(bfsTraversal(acqNode, relNode, null, tempWindowMin, tempWindowMax, false)); // Assert a path actually exists from acqNode -> relNode
									// Back edge found, but the acquire of both critical sections of the backedge need to reach either conflicting access
									if (!precision || (bfsTraversal(relNode.otherCriticalSectionNode, firstNode, secondNode, tempWindowMin, tempWindowMax, false) && bfsTraversal(acqNode, firstNode, secondNode, tempWindowMin, tempWindowMax, false))) {
										if (precision && VERBOSE_GRAPH) Assert.assertTrue(bfsTraversal(acqNode, relNode, null, tempWindowMin, tempWindowMax, false));//, "tempWindowMin: " + tempWindowMin + " | tempWindowMax: " + tempWindowMax + " | acqNode: " + acqNode.getNodeLabel() + ", eventNumber: " + acqNode.eventNumber + " | relNode: " + relNode.getNodeLabel() + ", eventNumber: " + relNode.eventNumber); // Assert a path actually exists from acqNode -> relNode
										// Add back edge and signal we should repeat this whole process
										Util.println("Found acq->rel that needs back edge: " + shadowLock + ", " + acqNode + "->" + relNode + " | " + acqNode.getExampleNumber() + "->" + relNode.getExampleNumber());
										EventNode.addEdge(acqNode.otherCriticalSectionNode, relNode.otherCriticalSectionNode);
										additionalBackEdges.add(new Edge(acqNode.otherCriticalSectionNode, relNode.otherCriticalSectionNode));
										windowMin = Math.min(windowMin, relNode.otherCriticalSectionNode.eventNumber);
										windowMax = Math.max(windowMax, acqNode.otherCriticalSectionNode.eventNumber);
										windowMin = Math.min(windowMin, relNode.eventNumber);
										windowMax = Math.max(windowMax, acqNode.eventNumber);
										addedBackEdge = true;
										// Add the acq/rel nodes of a new backedge to the set of starting accesses for new backedges
										// Mike says: I think acqNode.otherCriticalSectionNode isn't right
										/*
										acqSink.add(acqNode.otherCriticalSectionNode);
										relSource.add(relNode);
										*/
									}
								}
								//Forward Edges
								if (relNode.eventNumber > acqNode.eventNumber &&
									!relNode.otherCriticalSectionNode.equals(acqNode) && //make sure the relNode and acqNode are not the same critical section
									!containsNode(acqNode.otherCriticalSectionNode.sinkOrSinks, relNode.otherCriticalSectionNode)) { //don't add a forward edge if one has already been added
									long tempWindowMin = Math.min(windowMin, acqNode.otherCriticalSectionNode.eventNumber);
									tempWindowMin = Math.min(tempWindowMin, acqNode.eventNumber);
									long tempWindowMax = Math.max(windowMax, relNode.otherCriticalSectionNode.eventNumber);
									tempWindowMax = Math.max(tempWindowMax, relNode.eventNumber);
									// Forward edge found, but the acquire of both critical sections of the forwardedge need to reach either conflicting access
									if (!precision || (bfsTraversal(relNode.otherCriticalSectionNode, firstNode, secondNode, tempWindowMin, tempWindowMax, false) && bfsTraversal(acqNode, firstNode, secondNode, tempWindowMin, tempWindowMax, false))) {
										if (precision && VERBOSE_GRAPH) Assert.assertTrue(bfsTraversal(acqNode, relNode, null, tempWindowMin, tempWindowMax, false));//, "tempWindowMin: " + tempWindowMin + " | tempWindowMax: " + tempWindowMax + " | acqNode: " + acqNode.getNodeLabel() + ", eventNumber: " + acqNode.eventNumber + " | relNode: " + relNode.getNodeLabel() + ", eventNumber: " + relNode.eventNumber); // Assert a path actually exists from acqNode -> relNode
										// Add forward edge and signal we should repeat this whole process
										Util.println("Found rel->acq that needs forward edge: " + shadowLock + ", " + acqNode.otherCriticalSectionNode + "->" + relNode.otherCriticalSectionNode + " | " + acqNode.otherCriticalSectionNode.getExampleNumber() + "->" + relNode.otherCriticalSectionNode.getExampleNumber());
										EventNode.addEdge(acqNode.otherCriticalSectionNode, relNode.otherCriticalSectionNode);
										additionalForwardEdges.add(new Edge(acqNode.otherCriticalSectionNode, relNode.otherCriticalSectionNode));
										//Window Size should not have to be modified.
										//Since release nodes are found traversing forward and acquire nodes are found traversing backward
										//and release execution later than acquire means an added forward edge from release's acquire to acquire's release will already be within the window
										windowMin = Math.min(windowMin, acqNode.otherCriticalSectionNode.eventNumber);
										windowMax = Math.max(windowMax, relNode.otherCriticalSectionNode.eventNumber);
										windowMin = Math.min(windowMin, acqNode.eventNumber);
										windowMax = Math.max(windowMax, relNode.eventNumber);
										addedForwardEdge = true;
									}
								}
							}
						}
					}
				}
			}
		} while (addedBackEdge || addedForwardEdge);

		// Detect cycles

		final boolean useIterativeCycleDetection = true;
		Map<EventNode, List<EventNode>> firstCycleEdges = null;
		Map<EventNode, List<EventNode>> secondCycleEdges = null;
		if ( RR.wdcbGenerateFileForDetectedCycleOption.get() ) {
			firstCycleEdges = new HashMap<>();
			secondCycleEdges = new HashMap<>();
		}
		
		int black = EventNode.prepUniqueTraversal(2);
		int gray = black - 1;
		boolean secondCycleDetected;
		if (useIterativeCycleDetection) {
			secondCycleDetected = simplerIterativeDetectCycle(secondNode, false, gray, black, windowMin, windowMax);
			//secondCycleDetected = iterativeDfsDetectCycle(secondNode, false, gray, black, windowMin, windowMax, secondCycleEdges);
		} else {
			secondCycleDetected = dfsDetectCycle(secondNode, false, gray, black, windowMin, windowMax);
		}
		Util.println("Cycle reaches second node : " + secondCycleDetected + ". After " + iteration + " iterations.");

		black = EventNode.prepUniqueTraversal(2);
		gray = black - 1;
		boolean firstCycleDetected;
		if (useIterativeCycleDetection) {
			firstCycleDetected = simplerIterativeDetectCycle(firstNode, false, gray, black, windowMin, windowMax);
			//firstCycleDetected = iterativeDfsDetectCycle(firstNode, false, gray, black, windowMin, windowMax, firstCycleEdges);
		} else {
			firstCycleDetected = dfsDetectCycle(firstNode, false, gray, black, windowMin, windowMax);
		}
		Util.println("Cycle reaches first node : " + firstCycleDetected + ". After " + iteration + " iterations.");
				
		if ( RR.wdcbGenerateFileForDetectedCycleOption.get() ) {
			if ( firstCycleDetected ) {
				generateInputFileForGraphviz(firstCycleEdges, true, traverseFromAllEdges, precision);
			}	
			if ( secondCycleDetected ) {
				generateInputFileForGraphviz(secondCycleEdges, false, traverseFromAllEdges, precision);
			}
		}
		
		// Backward Reordering if no cycle was found for the WDC race
		LinkedList<EventNode> trPrime = null;
		if (!firstCycleDetected && !secondCycleDetected) {
			//Perform independent back traversal from both conflicting accesses in order to determine the reordering set [set of reachable nodes]
			trPrime = new LinkedList<>();
			HashSet<AcqRelNode> missingRelease = new HashSet<AcqRelNode>();
			boolean windowless = true;
			int reorder_white;
			int total_random_reorders = RR.wdcRandomReorderings.get();
			if (total_random_reorders > 0) {Util.log("Doing " + total_random_reorders + " random reorderings");}
			
			for (int reorders = 0; reorders <= total_random_reorders; reorders++) {
				missingRelease = new HashSet<AcqRelNode>();
				reorder_white = black+2; //The next bfsTraversal will increment black by 2 [Will include gray nodes from second bfsTraversal. More nodes to process than necessary, but not a correctness issue.]
				black = black+4;
				buildR(firstNode, secondNode, missingRelease, windowless, windowMin, windowMax, reorder_white);
				
				while (trPrime.isEmpty()) {
					black = black+1;
					trPrime = backReorderTrace(firstNode, secondNode, trPrime, reorder_white, reorders != 0, missingRelease);
					if (trPrime.isEmpty()) {
						Util.log("Backward Reordering got stuck when doing " + (reorders != 0 ? "random" : "latest-first") + " reordering!");
						if (total_random_reorders > 0) {
							Util.log("Got stuck during random reordering " + reorders);
						}
						Map<EventNode, List<EventNode>> bfsCycleEdges = new HashMap<>();
						black = EventNode.prepUniqueTraversal(2);
						gray = black - 1;
						iterativeDfsDetectCycle(firstNode, false, gray, black, windowMin, windowMax, bfsCycleEdges);
						generateInputFileForGraphviz(bfsCycleEdges, true, traverseFromAllEdges, precision);
						break;
					} else if (trPrime.size() == 1) {
						//Update R with missing release event and all reachable events
						missingRelease.add((AcqRelNode)trPrime.removeLast());
						//Perform independent back traversal from both conflicting accesses and all missing releases in order to determine the reordering set [set of reachable nodes]
						reorder_white = black+2; //The next bfsTraversal will increment black by 2 [Will include gray nodes from second bfsTraversal. More nodes to process than necessary, but not a correctness issue.]
						black = black+4+(2*missingRelease.size());
						buildR(firstNode, secondNode, missingRelease, windowless, windowMin, windowMax, reorder_white);
					}
				}
				
				// Reordering was successful
				if (commandDir != null && !trPrime.isEmpty()){
					printReordering(trPrime, firstNode, secondNode, commandDir);
				}

				// An empty trace indicates reordering got stuck, which code above has already reported.
				if (!trPrime.isEmpty() && DEBUG_ACCESS_INFO) {
					//Assert.assertFalse(trPrime.size() == 0, "Reordered trace is empty.");
					Collections.reverse(trPrime);
					Assert.assertTrue(forwardVerifyReorderedTrace(trPrime), "Reordered trace is invalid.");
				}

				if (VERBOSE_GRAPH) Assert.assertTrue(!trPrime.isEmpty());
				
				trPrime = new LinkedList<>();
			}
		}
		
		// Finally remove all of the added edges
		for (Edge e : initialEdgesToRemove) {
			EventNode.removeEdge(e.source, e.sink);
		}
		for (Edge e : additionalBackEdges) {
			EventNode.removeEdge(e.source, e.sink);
		}
		for (Edge e : additionalForwardEdges) {
			EventNode.removeEdge(e.source, e.sink);
		}
		
		return secondCycleDetected || firstCycleDetected;
	}
	
	static void buildR (EventNode firstNode, EventNode secondNode, HashSet<AcqRelNode> missingRelease, boolean windowless, long windowMin, long windowMax, int reorder_white) {
		if (VERBOSE_GRAPH) {
			EventNode eventOne = threadToFirstEventMap.get(0);
			eventOne = threadToFirstEventMap.get(0);
			Assert.assertTrue(eventOne.eventNumber == 1);
			bfsTraversal(secondNode, null, null, (windowless?Long.MIN_VALUE:windowMin), (windowless?Long.MAX_VALUE:windowMax), true);
			Assert.assertTrue(eventOne.myLatestTraversal == reorder_white);
			bfsTraversal(firstNode, null, null, (windowless?Long.MIN_VALUE:windowMin), (windowless?Long.MAX_VALUE:windowMax), true);
			Assert.assertTrue(eventOne.myLatestTraversal > reorder_white);
			for (AcqRelNode missingRel : missingRelease) {
				bfsTraversal(missingRel, null, null, (windowless?Long.MIN_VALUE:windowMin), (windowless?Long.MAX_VALUE:windowMax), true);
			}
		} else {
			bfsTraversal(secondNode, null, null, (windowless?Long.MIN_VALUE:windowMin), (windowless?Long.MAX_VALUE:windowMax), true);
			bfsTraversal(firstNode, null, null, (windowless?Long.MIN_VALUE:windowMin), (windowless?Long.MAX_VALUE:windowMax), true);
			for (AcqRelNode missingRel : missingRelease) {
				bfsTraversal(missingRel, null, null, (windowless?Long.MIN_VALUE:windowMin), (windowless?Long.MAX_VALUE:windowMax), true);
			}
		}
	}
	
	static LinkedList<EventNode> backReorderTrace(RdWrNode firstNode, RdWrNode secondNode, LinkedList<EventNode> trPrime, int white, Boolean randomReordering, HashSet<AcqRelNode> missingRelease) {
		int black = prepUniqueTraversal();
		HashMap<Integer, EventNode> traverse = new HashMap<Integer, EventNode>();
		for (EventNode missingRel : missingRelease) {
			if (!traverse.containsKey(missingRel.threadID) || traverse.get(missingRel.threadID).eventNumber < missingRel.eventNumber) {
				traverse.put(missingRel.threadID, missingRel);
			}
		}
		trPrime.add(secondNode);
		secondNode.myLatestTraversal = black;
		trPrime.add(firstNode);
		firstNode.myLatestTraversal = black;
		
		for (EventNode firstSource : firstNode.sourceOrSources) {
			if (firstSource.myLatestTraversal >= white && firstSource.myLatestTraversal != black) {
				if (!traverse.containsKey(firstSource.threadID) || traverse.get(firstSource.threadID).eventNumber < firstSource.eventNumber) {
					traverse.put(firstSource.threadID, firstSource);
				}
			}
		}
		for (EventNode secondSource : secondNode.sourceOrSources) {
			if (secondSource.myLatestTraversal >= white && secondSource.myLatestTraversal != black) {
				if (!traverse.containsKey(secondSource.threadID) || traverse.get(secondSource.threadID).eventNumber < secondSource.eventNumber) {
					traverse.put(secondSource.threadID, secondSource);
				}
			}
		}
		//heldLocks keeps track of the inner most active critical section per thread
		//Each acquire EventNode of a critical sections points to the acquire surrounding it, if any
		//Traversal through these acquires will determine if a lock is currently held by a thread
		HashSet<AcqRelNode> heldLocks = new HashSet<AcqRelNode>();
		HashSet<ShadowLock> onceHeldLocks = new HashSet<ShadowLock>();
		AcqRelNode surroundingCS = firstNode.surroundingCriticalSection;
		while (surroundingCS != null) {
			heldLocks.add(surroundingCS);
			surroundingCS = surroundingCS.surroundingCriticalSection;
		}
		surroundingCS = secondNode.surroundingCriticalSection;
		while (surroundingCS != null) {
			heldLocks.add(surroundingCS);
			surroundingCS = surroundingCS.surroundingCriticalSection;
		}
		EventNode latestCheck = null;
		HashSet<Integer> attemptedEvents = new HashSet<>(); // Keys for events that we attempted to add to trace but failed
		while (!traverse.isEmpty()) {
			EventNode e;
			if (randomReordering) {
				e = reorderRandom(traverse, attemptedEvents);
			} else {
				e = reorderLatestFirst(traverse, latestCheck, attemptedEvents);
			}

			if (e == null) {
				backReorderStuck(trPrime, white, black, traverse, heldLocks, latestCheck, attemptedEvents);
				return new LinkedList<>();
			}
			// Check if event e satisfies Program Order (PO) and Conflicting Accesses (CA)
			boolean sinkCheck = true;
			for (EventNode ePrime : e.sinkOrSinks) {
				if (ePrime.myLatestTraversal >= white && ePrime.myLatestTraversal != black) {
					sinkCheck = false;
					break;
				}
			}
			
			// Check for missing release events
			if (e.surroundingCriticalSection != null) {
				AcqRelNode checkMissingRel = e.surroundingCriticalSection.getOtherCriticalSectionNode();
				if (e instanceof AcqRelNode && !((AcqRelNode)e).isAcquire() && ((AcqRelNode)e).getOtherCriticalSectionNode().surroundingCriticalSection != null) {
					checkMissingRel = ((AcqRelNode)e).getOtherCriticalSectionNode().surroundingCriticalSection.getOtherCriticalSectionNode();
				}
				if (onceHeldLocks.contains(checkMissingRel.shadowLock) && checkMissingRel.myLatestTraversal < white /*missing release is not in R*/) {
					trPrime.clear();
					trPrime.add(checkMissingRel);
					return trPrime;
				}
			}
			
			// Check if event e satisfies Lock Semantics (LS)
			boolean lockCheck = true;
			surroundingCS = e.surroundingCriticalSection;
			while (surroundingCS != null) {
				if (!heldLocks.contains(surroundingCS) && ongoingCriticalSection(heldLocks, surroundingCS.shadowLock)) {
					lockCheck = false;
					break;
				}
				if (surroundingCS.surroundingCriticalSection != null && VERBOSE_GRAPH) Assert.assertTrue(surroundingCS.shadowLock != surroundingCS.surroundingCriticalSection.shadowLock);
				surroundingCS = surroundingCS.surroundingCriticalSection;
			}
			if (lockCheck && e instanceof AcqRelNode) {
				AcqRelNode eAcq = (AcqRelNode) e;
				if (eAcq.isAcquire() && !heldLocks.contains(eAcq) && ongoingCriticalSection(heldLocks, eAcq.shadowLock)) {
					lockCheck = false;
				}
			}
			
			// Add event e to the reordered trace tr'
			if (sinkCheck && lockCheck) {
				trPrime.add(e);
				attemptedEvents = new HashSet<>();
				e.myLatestTraversal = black;
				surroundingCS = e.surroundingCriticalSection;
				if (e instanceof AcqRelNode && ((AcqRelNode) e).isAcquire()) {
					onceHeldLocks.add(((AcqRelNode)e).shadowLock);
					heldLocks.remove(e);
				} else {
					surroundingCS = e.surroundingCriticalSection;
					while (surroundingCS != null) {
						if (!ongoingCriticalSection(heldLocks, surroundingCS.shadowLock)) {
							heldLocks.add(surroundingCS);
						}
						surroundingCS = surroundingCS.surroundingCriticalSection;
					}
				}
				//Either PO edge was added or the event was the first in the thread
				if (VERBOSE_GRAPH) Assert.assertTrue(traverse.containsKey(e.threadID) || e == threadToFirstEventMap.get(e.threadID), "event is: " + e.getNodeLabel() + " | thread: " + e.threadID + " | eventNumber: " + e.eventNumber);
				latestCheck = null;
				traverse.remove(e.threadID);
				for (EventNode eSource : e.sourceOrSources) {
					if (eSource.myLatestTraversal >= white && eSource.myLatestTraversal != black) {
						if (!traverse.containsKey(eSource.threadID) || traverse.get(eSource.threadID).eventNumber < eSource.eventNumber) {
							traverse.put(eSource.threadID, eSource);
						}
					}
				}
			} else {
				latestCheck = e;
			}
		}
		return trPrime;
	}

	private static EventNode reorderRandom(HashMap<Integer, EventNode> traverse, HashSet<Integer> attemptedEvents) {
		// Find the keys for all events we haven't attempted yet
		Set<Integer> events = new HashSet<>(traverse.keySet());
		events.removeAll(attemptedEvents);
		// Randomly pick an event
		Vector<Integer> possibleTids = new Vector<Integer>(events);
		if (possibleTids.size() != 0) {
			Integer tid = possibleTids.elementAt(new Random().nextInt(possibleTids.size()));
			attemptedEvents.add(tid);
            return traverse.get(tid);
        }
		return null;
	}

	// Could refactor reorderRandom and reorderLatestFirst into a single method
	private static EventNode reorderLatestFirst(HashMap<Integer, EventNode> traverse, EventNode latestCheck, HashSet<Integer> attemptedEvents) {
		// Find the keys for all events we haven't attempted yet
		Set<Integer> events = new HashSet<>(traverse.keySet());
		events.removeAll(attemptedEvents);
		EventNode e = null;
		int eTid = -1;
		for (int tid : events) {
            if (e == null) {
                if (latestCheck == null) {
                    e = traverse.get(tid);
                    eTid = tid;
                } else if (traverse.get(tid).eventNumber <= latestCheck.eventNumber) {
                    e = traverse.get(tid);
                    eTid = tid;
                }
            } else if (latestCheck == null) {
                if (e.eventNumber <= traverse.get(tid).eventNumber) {
                    e = traverse.get(tid);
                    eTid = tid;
                }
            } else if (e.eventNumber <= traverse.get(tid).eventNumber && traverse.get(tid).eventNumber <= latestCheck.eventNumber) {
                e = traverse.get(tid);
                eTid = tid;
            }
        }
		if (e != null) attemptedEvents.add(eTid);		
		return e;
	}

	private static boolean backReorderStuck(LinkedList<EventNode> trPrime, int white, int black, HashMap<Integer, EventNode> traverse, HashSet<AcqRelNode> heldLocks, EventNode latestCheck, HashSet<Integer> attemptedEvents) {
		Util.log("black: " + black + " , white: " + white);
		Util.log("trPrime so far: ");
		for (EventNode node : trPrime) {
            Util.log(node.getNodeLabel() + " | eventNumber: " + node.eventNumber + " | surroundingCS: " + node.surroundingCriticalSection + " | myLatestTraversal: " + node.myLatestTraversal);
        }
		Util.log("BackReorder Set Getting Stuck: latestCheck: " + latestCheck + " | eventNumber: " + latestCheck.eventNumber + " | surroundingCS: " + latestCheck.surroundingCriticalSection);
		Util.log("attempted events at this point:");
		for (Integer tid : attemptedEvents) {
			EventNode e = traverse.get(tid);
			Util.log("attempted " + e.getNodeLabel() + " | eventNumber: " + e.eventNumber);
		}
		for (int eTid : traverse.keySet()) {
            boolean outgoing_edge_check = false;
            EventNode eCheck = traverse.get(eTid);
            for (EventNode ePrime : eCheck.sinkOrSinks) {
                if (ePrime.myLatestTraversal >= white && ePrime.myLatestTraversal != black) {
                    outgoing_edge_check = true; //true is bad
                }
            }
            Util.log(traverse.get(eTid).getNodeLabel() + " | eventNumber: " + traverse.get(eTid).eventNumber + " | surroundingCS: " + traverse.get(eTid).surroundingCriticalSection + " | myLatestTraversal: " + traverse.get(eTid).myLatestTraversal + " | could not be added due to sink node: " + outgoing_edge_check);
            if (outgoing_edge_check) {
                for (EventNode ePrime : eCheck.sinkOrSinks) {
                    if (ePrime.myLatestTraversal >= white) {
                        Util.log("--sink--> " + ePrime.getNodeLabel() + " | eventNumber: " + ePrime.eventNumber + " | surroundingCS: " + ePrime.surroundingCriticalSection);
                        for (EventNode ePrimePrime : ePrime.sinkOrSinks) {
                            if (ePrimePrime.myLatestTraversal >= white) {
                                Util.log("--sink--of sink--> " + ePrimePrime.getNodeLabel() + " | eventNumber: " + ePrimePrime.eventNumber + " | surroundingCS: " + ePrimePrime.surroundingCriticalSection);
                            }
                        }
                    }
                }
            }
        }
		Util.log("heldLocks:");
		for (AcqRelNode eAcq : heldLocks) {
            Util.log(eAcq.getNodeLabel());
        }

        // Make sure all attempted events are invalid
        if (VERBOSE_GRAPH) {
			for (Integer eTid : attemptedEvents) {
				EventNode e = traverse.get(eTid);
				boolean sinkCheck = false;
				for (EventNode ePrime : e.sinkOrSinks) {
					if (ePrime.myLatestTraversal >= white && ePrime.myLatestTraversal != black) {
						sinkCheck = true;
					}
				}
				boolean lockCheck = false;
				if (e instanceof AcqRelNode) {
					AcqRelNode eAcq = (AcqRelNode) e;
					HashSet<ShadowLock> eAcqSurrounding = new HashSet<>();
					for (AcqRelNode surrounding = eAcq; surrounding.surroundingCriticalSection != null; surrounding = surrounding.surroundingCriticalSection) {
						eAcqSurrounding.add(surrounding.shadowLock);
					}
					if (!eAcq.isAcquire) {
						for (AcqRelNode held : heldLocks) {
							if (eAcqSurrounding.contains(held.shadowLock)) {
								lockCheck = true;
							}
						}
					}
				}
				if (!(sinkCheck || lockCheck)) {
					Util.log("Event could be added to the trace: " + e.getNodeLabel() + " | eventNumber: " + e.eventNumber + " | lockCheck: " + lockCheck + " | sinkCheck: " + sinkCheck);
				}
			}
		}
		return false;
	}

	static boolean ongoingCriticalSection(HashSet<AcqRelNode> heldLocks, ShadowLock e) {
		for (AcqRelNode lock : heldLocks) {
			if (e == lock.shadowLock) {
				return true;
			}
		}
		return false;
	}
	
	static boolean bfsTraversal(EventNode startingNode, EventNode firstNode, EventNode secondNode, long windowMin, long windowMax, boolean reorderMark) {
		ArrayDeque<EventNode> WDCBGraph = new ArrayDeque<EventNode>();
		int black = EventNode.prepUniqueTraversal(2);
		int gray = black - 1;
		
		WDCBGraph.add(startingNode);
		startingNode.myLatestTraversal = gray;
		
		while (!WDCBGraph.isEmpty()) {
			EventNode node = WDCBGraph.pop();
			if (node.myLatestTraversal != black) {
				if (node.eventNumber >= windowMin && node.eventNumber <= windowMax) {
					if ((firstNode != null && (reorderMark ? containsNode(node.sourceOrSources, firstNode) : containsNode(node.sinkOrSinks, firstNode))) || (secondNode != null && (reorderMark ? containsNode(node.sourceOrSources, secondNode) : containsNode(node.sinkOrSinks, secondNode)))) {
						return true;
					}
					for (EventNode sinkOrSource : (reorderMark ? node.sourceOrSources : node.sinkOrSinks)) {
						if (sinkOrSource.myLatestTraversal < gray) {
							WDCBGraph.add(sinkOrSource);
							sinkOrSource.myLatestTraversal = gray;
						}
					}
				}
				node.myLatestTraversal = black;
				if (VERBOSE_GRAPH) Assert.assertTrue(node.eventNumber > -2);
			}
		}
		return false;
	}
	
	@Deprecated
	static boolean bfsValidatePath(ArrayDeque<EventNode> grayNodes, EventNode acqNode, EventNode relNode, long windowMax, long windowMin) {
		int black = EventNode.prepUniqueTraversal(2);
		int gray = black - 1;
		grayNodes.clear();
		
		grayNodes.add(acqNode);
		acqNode.myLatestTraversal = gray;
		
		while (!grayNodes.isEmpty()) {
			EventNode node = grayNodes.removeFirst();
			if (node.myLatestTraversal != black) {
				if (node.eventNumber >= windowMin && node.eventNumber <= windowMax) {
					if (containsNode(node.sinkOrSinks, relNode)) {
						return true;
					}
					for (EventNode sinkNode : node.sinkOrSinks) {
						if (sinkNode.myLatestTraversal < gray) {
							grayNodes.add(sinkNode);
							sinkNode.myLatestTraversal = gray;
						}
					}
				}
				node.myLatestTraversal = black;
			}
		}
		return false;
	}	
	
	@Deprecated
	static boolean bfsValidateBackedge(ArrayDeque<EventNode> grayNodes, AcqRelNode ARNode, RdWrNode firstNode, RdWrNode secondNode, long windowMax, long windowMin) {
		int black = EventNode.prepUniqueTraversal(2);
		int gray = black - 1;
		grayNodes.clear();
		
		if (ARNode.isAcquire()) { //validate from the acquire of the backedge with a later event number  
			grayNodes.add(ARNode);
			ARNode.myLatestTraversal = gray;
		} else { //validate from the acquire of the backedge with an earlier event number
			grayNodes.add(ARNode.otherCriticalSectionNode);
			ARNode.otherCriticalSectionNode.myLatestTraversal = gray;
		}
		
		while (!grayNodes.isEmpty()) {
			EventNode node = grayNodes.removeFirst();
			if (node.myLatestTraversal != black) {
				if (node.eventNumber >= windowMin && node.eventNumber <= windowMax) {
					if (containsNode(node.sinkOrSinks, firstNode) || containsNode(node.sinkOrSinks, secondNode)) {
						return true;
					}
					for (EventNode sinkNode : node.sinkOrSinks) {
						if (sinkNode.myLatestTraversal < gray) {
							grayNodes.add(sinkNode);
							sinkNode.myLatestTraversal = gray;
						}
					}
				}
				node.myLatestTraversal = black;
			}
		}
		return false;
	}
	
	@Deprecated
	static void bfsReachability(EventNode startingNode, int gray, int black, long windowMin, long windowMax) {
		ArrayDeque<EventNode> WDCBGraph = new ArrayDeque<EventNode>();
		WDCBGraph.add(startingNode);
		while(!WDCBGraph.isEmpty()) {
			EventNode node = WDCBGraph.pop();
			if (node.eventNumber >= windowMin && node.eventNumber <= windowMax) {
				if (node.myLatestTraversal != black) {
					node.myLatestTraversal = gray;
					for (EventNode sourceNode : node.sourceOrSources) {
						if (sourceNode.myLatestTraversal != black) {
							WDCBGraph.add(sourceNode);
						}
					}
					node.myLatestTraversal = black;
				}
			}
		}
	}
	
	static boolean dfsDetectCycle(EventNode node, boolean isForward, int gray, int black, long windowMin, long windowMax) {
		if (node.eventNumber >= windowMin && node.eventNumber <= windowMax) {
			if (node.myLatestTraversal != black) {
				if (node.myLatestTraversal == gray) {
					return true;
				}
				node.myLatestTraversal = gray;
				for (EventNode predOrSucc : (isForward ? node.sinkOrSinks : node.sourceOrSources)) {
					boolean cycleDetected = dfsDetectCycle(predOrSucc, isForward, gray, black, windowMin, windowMax);
					if (cycleDetected) {
						return true;
					}
				}
				node.myLatestTraversal = black;
			}
		}
		return false;
	}
	
	static boolean simplerIterativeDetectCycle(EventNode node, boolean isForward, int gray, int black, long windowMin, long windowMax) {
		Stack<EventNode> nodeStack = new Stack<EventNode>();
		Stack<Iterator<EventNode>> iterStack = new Stack<Iterator<EventNode>>();
		
		start:
		while (true) {
			Iterator<EventNode> iter;
			if (node.myLatestTraversal != black) {
				if (node.myLatestTraversal == gray) {
					return true;
				}
				node.myLatestTraversal = gray;
				iter = (isForward ? node.sinkOrSinks : node.sourceOrSources).iterator();
			} else {
				if (nodeStack.isEmpty()) {
					return false;
				}
				node = nodeStack.pop();
				iter = iterStack.pop();
			}
			while (iter.hasNext()) {
				EventNode predOrSucc = iter.next();
				if (predOrSucc.eventNumber >= windowMin && predOrSucc.eventNumber <= windowMax) {
					nodeStack.push(node);
					iterStack.push(iter);
					node = predOrSucc;
					continue start;
				}
			}
			node.myLatestTraversal = black;
		}
	}
	
	// Used by iterative DFS-based cycle detection
	static class EventNodeDepth {
		final EventNode node;
		final int depth;
		EventNodeDepth(EventNode node, int depth) {
			this.node = node;
			this.depth = depth;
		}
	}
	
	static boolean iterativeDfsDetectCycle(EventNode node, boolean isForward, int gray, int black, long windowMin,
			long windowMax, Map<EventNode, List<EventNode>> cycleEdges) {

		Stack<EventNodeDepth> stack = new Stack<EventNodeDepth>();
		List<EventNodeDepth> currentTrace = new ArrayList<EventNodeDepth>();
		int currentDepth = 0;
		stack.push(new EventNodeDepth(node, 1));
		while (!stack.isEmpty()) {
			EventNodeDepth currentNodeDepth = stack.pop();
			EventNode currentNode = currentNodeDepth.node;
			
			if (currentNode.eventNumber >= windowMin && currentNode.eventNumber <= windowMax) {
				if (currentNode.myLatestTraversal != black) {
					if (currentNodeDepth.depth <= currentDepth) {
						while (currentTrace.size() > 0 && (currentTrace.get(currentTrace.size() - 1)).depth >= currentNodeDepth.depth) {
							currentTrace.get(currentTrace.size() - 1).node.myLatestTraversal = black;
							currentTrace.remove(currentTrace.size() - 1);
						}
					}
					
					// TODO: This is expensive for long paths!
					for (EventNodeDepth eventNodeDepth : currentTrace) {
						if (eventNodeDepth.node == currentNode) {
							return true;
						}
					}
					currentTrace.add(currentNodeDepth);
					currentDepth = currentNodeDepth.depth;
					
					for (EventNode predOrSucc : (isForward ? currentNode.sinkOrSinks : currentNode.sourceOrSources)) {
						// TODO: Mike says: Are the the following two lines correct? I modified the functionality somewhat from the original. 
						//currentNodeDepth.depth = currentDepth + 1;
						stack.push(new EventNodeDepth(predOrSucc, currentDepth + 1));
						
						if (cycleEdges != null) {
							if (!cycleEdges.containsKey(predOrSucc)) {
								cycleEdges.put(predOrSucc, new ArrayList<EventNode>());
							}
							cycleEdges.get(predOrSucc).add(currentNode);
						}
					}
				}
			}
		}
		return false;
	}
	
	static void printReordering(LinkedList<EventNode> trPrime, RdWrNode firstNode, RdWrNode secondNode, File commandDir) {
		try {
			PrintWriter input = new PrintWriter(commandDir+"/wdc_race_" + firstNode.eventNumber + "_" + secondNode.eventNumber);
			for (EventNode trPrimeEvent : trPrime) {
				if (trPrimeEvent instanceof AcqRelNode) {
					AcqRelNode ARPrimeEvent = (AcqRelNode)trPrimeEvent;
					String ARPrimeName = Util.objectToIdentityString(ARPrimeEvent.shadowLock.getLock());
					input.println("T" + ARPrimeEvent.threadID + ":" + (ARPrimeEvent.isAcquire() ? "acq(" : "rel(") + ARPrimeName + "):" 
									+ ARPrimeEvent.eventNumber + "|" + ARPrimeEvent.getExampleNumber());
				} else if (trPrimeEvent instanceof RdWrNode) {
					RdWrNode RWPrimeEvent = (RdWrNode)trPrimeEvent;
					input.println("T" + trPrimeEvent.threadID + ":" + (RWPrimeEvent.isWrite() ? "wr(" : "rd(") + RWPrimeEvent.getFieldName() + "):"
									+ RWPrimeEvent.eventNumber + "|" + RWPrimeEvent.getExampleNumber());
				} else {
					input.println("T" + trPrimeEvent.threadID + ":" + trPrimeEvent.eventNumber + "|" + trPrimeEvent.getExampleNumber());
				}
			}
			input.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static void generateInputFileForGraphviz(Map<EventNode, List<EventNode>> edges, boolean isFirst, boolean isTraverseFromAllEdges, boolean precision){
		 
		String fileName = (isFirst ? "first" : "second") + "Cycle" + "_TA-" + isTraverseFromAllEdges + "_P-" + precision + ".txt"; 
		try {
			PrintWriter printWriter = new PrintWriter(fileName, "UTF-8");
			
			printWriter.println("digraph G");
			printWriter.println("{");
			printWriter.println("rankdir=TB");
			printWriter.println("newrank=true");
			printWriter.println("node [shape = circle];");
	
		    Map<Integer, List<EventNode>> threadIdToThreadEvents = new HashMap<>();
		    Iterator it = edges.entrySet().iterator();
		    while (it.hasNext()) {
		    	Map.Entry pair = (Map.Entry)it.next();
		    	addEventToItsThreadList(threadIdToThreadEvents, (EventNode)pair.getKey());
		        for (EventNode eventNode : (List<EventNode>)pair.getValue()) {
		        	addEventToItsThreadList(threadIdToThreadEvents, eventNode);
		        }		  		        
		    }
		    
		    it = threadIdToThreadEvents.entrySet().iterator();
		    while (it.hasNext()) {
		    	Map.Entry pair = (Map.Entry)it.next();
		    	printWriter.println("subgraph Cluster_" + (int)pair.getKey() + " {");
		    	printWriter.println("label = T" + (int)pair.getKey());
		        for (EventNode eventNode : (List<EventNode>)pair.getValue()) {
		        	String nodeLabel = eventNode.getNodeLabel() + "- #" + eventNode.eventNumber;	
		        	printWriter.println( "node" + eventNode.eventNumber + " [label = \"" + nodeLabel + "\"];");			       
		        }
		        printWriter.println("}");
		    }
		    
		    it = edges.entrySet().iterator();
		    while (it.hasNext()) {
		        Map.Entry pair = (Map.Entry)it.next();
		        for (EventNode child : (List<EventNode>) pair.getValue()) {		        				        
		        	long keyEventNumber = ((EventNode)pair.getKey()).eventNumber;
		        	long tail = keyEventNumber;		        
		        	long head = child.eventNumber;
		        	if ( keyEventNumber > child.eventNumber ) {
		        		tail = child.eventNumber;
		        		head = keyEventNumber;
		        	}
			        printWriter.println("\"node" + tail + "\" -> \"node" + head + "\"" + 
		        	(keyEventNumber > child.eventNumber ? " [dir=\"back\"]" : "") + ";");
		        }
		    }
		    
//		    it = threadIdToThreadEvents.entrySet().iterator();
//		    while (it.hasNext()) {
//		    	printWriter.print("{rank=same");
//		    	Map.Entry pair = (Map.Entry)it.next();		        
//		        for (Long eventNumber : (List<Long>)pair.getValue()) {
//		        	printWriter.print(" node" + eventNumber);
//		        }
//		        printWriter.println("}");
//		        
//		    }

		    printWriter.println("}");
		    printWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	private static void addEventToItsThreadList(Map<Integer, List<EventNode>> threadIdToThreadEvents, EventNode eventNode) {
		if (!threadIdToThreadEvents.containsKey(eventNode.getThreadId())) {
			threadIdToThreadEvents.put(eventNode.getThreadId(), new ArrayList<EventNode>());
		}
		threadIdToThreadEvents.get(eventNode.getThreadId()).add(eventNode);
	}
	
	public String getNodeLabel () {
		if (DEBUG_EXNUM_LABEL) {
			return nodeLabelMap.get(this);
		} else {
			return "?";
		}
	}
	
	public int getThreadId(){
		return this.threadID;
	}
	

	// Collect Latex output to examine events included in the cycles leading to at least one conflicting access of a WCP/WDC race.
	@Deprecated
	static void generateLatex(EventNode firstNode, EventNode secondNode, TreeMap<Long, EventNode> unorderedEvents, boolean secondCycleDetected, boolean firstCycleDetected) {
		Assert.assertTrue(DEBUG_ACCESS_INFO);
		if (secondCycleDetected || firstCycleDetected) {
			try {
				BufferedWriter collectLatex = new BufferedWriter(new FileWriter("latex_output/LatexTrace_"+firstNode.getExampleNumber()+"_"+secondNode.getExampleNumber()+".tex"));
				collectLatex.write("\\documentclass{article}\n\\begin{document}\\begin{figure}\n\\centering\n\\begin{tabular}{");
				for (int i = 0; i < ShadowThread.maxActiveThreads(); i++) {
					collectLatex.write("l");
				}
				collectLatex.write("}\n");
				for (int i = 0; i < ShadowThread.maxActiveThreads()-1; i++) {
					collectLatex.write("T" + i + " & ");
				}
				collectLatex.write("T" + (ShadowThread.maxActiveThreads()-1) + " \\\\\\hline\n");

				Iterator<Entry<Long, EventNode>> totalOrder = unorderedEvents.entrySet().iterator();
				while (totalOrder.hasNext()) {
					EventNode orderedEvent = (EventNode) totalOrder.next().getValue();
					if (orderedEvent instanceof RdWrNode) {
						RdWrDebugNode orderedRdWr = (RdWrDebugNode) orderedEvent;
						for (int i = 0; i < orderedRdWr.threadID; i++) {
							collectLatex.write("&");
						}
						// TODO: One event may have more than one write/read, this only makes sense if merging is disabled
						collectLatex.write("wr(V"+orderedRdWr.getAccesses().firstElement().var.hashCode()+")");
						for (int i = orderedRdWr.threadID+1; i < ShadowThread.maxActiveThreads(); i++) {
							collectLatex.write("&");
						}
						collectLatex.write("\\\\\n");
					} else if (orderedEvent instanceof AcqRelNode) {
						AcqRelNode orderedAcqRel = (AcqRelNode) orderedEvent;
						if (orderedAcqRel.isAcquire) {
							for (int i = 0; i < orderedAcqRel.threadID; i++) {
								collectLatex.write("&");
							}
							collectLatex.write("acq(L"+orderedAcqRel.shadowLock.getLock().hashCode()+")");
							for (int i = orderedAcqRel.threadID+1; i < ShadowThread.maxActiveThreads(); i++) {
								collectLatex.write("&");
							}
							collectLatex.write("\\\\\n");
						} else {
							for (int i = 0; i < orderedAcqRel.threadID; i++) {
								collectLatex.write("&");
							}
							collectLatex.write("rel(L"+orderedAcqRel.shadowLock.getLock().hashCode()+")");
							for (int i = orderedAcqRel.threadID+1; i < ShadowThread.maxActiveThreads(); i++) {
								collectLatex.write("&");
							}
							collectLatex.write("\\\\\n");
						}
					}
				}
				
				collectLatex.write("\\hline\n\\end{tabular}\n\\end{figure}\n\\end{document}");
				collectLatex.close();
			} catch (IOException e) {
				Util.println(e.getMessage());
			}
		}
	}
	
	public int getLatestTraversal() {
		return myLatestTraversal;
	}
	
	@Override
	public String toString() {
		return String.valueOf(eventNumber);
	}
	
	static void addEventToThreadToItsFirstEventsMap(EventNode eventNode) {
		if (VERBOSE_GRAPH) Assert.assertTrue(eventNode.eventNumber >= 0 || ShadowThread.get(eventNode.threadID).getThread().getName().equals("Finalizer"));
		if ( !threadToFirstEventMap.containsKey(eventNode.threadID) ) {
			threadToFirstEventMap.put(eventNode.threadID, eventNode);
		} else {
			if ( threadToFirstEventMap.get(eventNode.threadID).eventNumber > eventNode.eventNumber ) {
				threadToFirstEventMap.put(eventNode.threadID, eventNode);
			}
		}
	}
	
	private static EventNode getOriginalTraceNextEvent(Map<Integer, EventNode> threadToItsNextEventMap){
		Iterator<Entry<Integer, EventNode>> it = threadToItsNextEventMap.entrySet().iterator();
	    EventNode nextTraceEvent = null;
		while (it.hasNext()) {
	    	Entry<Integer, EventNode> pair = it.next();
	    	if ( nextTraceEvent == null || ( pair.getValue() != null && (pair.getValue()).eventNumber < nextTraceEvent.eventNumber) ) {
	    		nextTraceEvent = pair.getValue();
	    	}
	    }		
		if ( nextTraceEvent != null ) {
			EventNode nextThreadEvent = null;
			for (EventNode sink : nextTraceEvent.sinkOrSinks) {
				if ( (sink.threadID == nextTraceEvent.threadID) && 
						(nextThreadEvent == null || (nextTraceEvent.eventNumber < sink.eventNumber && sink.eventNumber < nextThreadEvent.eventNumber)) ) {
					nextThreadEvent = sink;
				}
			}
			threadToItsNextEventMap.put(nextTraceEvent.threadID, nextThreadEvent);
		}		
		return nextTraceEvent;
	}


	private static boolean forwardVerifyReorderedTrace(LinkedList<EventNode> trPrime) {
		Map<Integer, EventNode> lastEvent = new HashMap<>(); // Maps threads to the last event by them
		Map<ShadowLock, Integer> lockHeldBy = new HashMap<>(); // Maps locks to threads holding them
		Map<ShadowVar, Map<Integer, RdWrDebugNode.Access>> lastAccesses = new HashMap<>(); // Maps variables to the last write or last read by each thread

		for (EventNode event : trPrime) {
			// PO
			EventNode last = lastEvent.get(event.threadID);
			if (last != null) {
				if (last.eventNumber >= event.eventNumber) {
					Util.log("PO ordering violated in the reordered trace by events " + last.eventNumber + " and " + event.eventNumber + " by T" + event.threadID);
					return false;
				}
				if (!isPreviousEvent(event, last)) {
					return false;
				}
			}
			lastEvent.put(event.threadID, event);

			// LS
			if (event instanceof AcqRelNode) {
			AcqRelNode syncEvent = (AcqRelNode) event;
				ShadowLock sl = syncEvent.shadowLock;
				if (syncEvent.isAcquire) {
					if (lockHeldBy.containsKey(sl)) {
						Util.log("Lock semantics violated in the reordered trace, T" + event.threadID
								+ " is trying to acquire lock " + Util.objectToIdentityString(sl.getLock())
								+ " which is already held by T" + lockHeldBy.get(sl));
						return false;
					}
					lockHeldBy.put(sl, event.threadID);
				} else { // syncEvent is release
					if (!lockHeldBy.containsKey(sl)) {
						Util.log("Lock semantics violated in the reordered trace, T" + event.threadID
								+ " is trying to release lock " + Util.objectToIdentityString(sl.getLock())
								+ " which it is not holding!");
						return false;
					}
					lockHeldBy.remove(sl);
				}
			}

			// CA
			if (event instanceof RdWrNode) {
				// Due to merging, each event contains multiple accesses
				RdWrDebugNode currEvent = (RdWrDebugNode) event;
				for (RdWrDebugNode.Access currAcc : currEvent.getAccesses()) {
					if (VERBOSE_GRAPH) Assert.assertTrue(currAcc.inEvent.equals(currEvent));
					Map<Integer, RdWrDebugNode.Access> prevAccs = lastAccesses.get(currAcc.var);
					if (prevAccs == null) { // first access
						// No checks here, if CA is violated we'll catch that on the next access
						prevAccs = new HashMap<>();
						prevAccs.put(currEvent.threadID, currAcc);
						lastAccesses.put(currAcc.var, prevAccs);
					} else { // not the first access, there must be at least 1 event in prevAccs
						boolean lastAccIsWrite = prevAccs.values().iterator().next().isWrite;
						// To be conflicting, at least one of the accesses must be a write
						if (lastAccIsWrite || currAcc.isWrite) {
							for (RdWrDebugNode.Access lastAcc : prevAccs.values()) {
								if (lastAcc.eventNumber() > currAcc.eventNumber()) {
									Util.log("CA violated in the reordered trace, conflicting accesses "
											+ lastAcc + " in " + lastAcc.inEvent + " and " + currAcc + " in " + currAcc.inEvent
											+ " are flipped in the reordered trace.");
									printSinks(lastAcc.inEvent);
									printSinks(currAcc.inEvent);
									return false;
								}
							}
						}
						// Applying a FastTrack-like optimization here. Writes are sequentially consistent, so we only
						// need the previous write to enforce write-write or write-read CA. To enforce CA on read-write
						// accesses, we need all previous reads.
						if (currAcc.isWrite || lastAccIsWrite) {
							// We can throw away previous accesses if either:
							// 1. Current is write, next check will be write-write or write-read
							// 2. Last was a write and current is a read, so there are no previous reads to preserve
							prevAccs.clear();
						}
						prevAccs.put(currEvent.threadID, currAcc);
					}
				}
			}
		}


		return true;
	}

	private static void printSinks(RdWrDebugNode lastAcc) {
		Util.log("Sinks for access " + lastAcc.getNodeLabel() + " | eventNumber: " + lastAcc.eventNumber + " | surroundingCS: " + lastAcc.surroundingCriticalSection);
		for (EventNode ePrime : lastAcc.sinkOrSinks) {
            Util.log("--sink--> " + ePrime.getNodeLabel() + " | eventNumber: " + ePrime.eventNumber + " | surroundingCS: " + ePrime.surroundingCriticalSection);
            for (EventNode ePrimePrime : ePrime.sinkOrSinks) {
                Util.log("--sink--of sink--> " + ePrimePrime.getNodeLabel() + " | eventNumber: " + ePrimePrime.eventNumber + " | surroundingCS: " + ePrimePrime.surroundingCriticalSection);
            }
        }
	}

	// Checks that prev is actually just before event in PO
	private static boolean isPreviousEvent(EventNode event, EventNode prev) {
		EventNode prevPO = null;
		for (EventNode currSource : event.sourceOrSources) {
            if (event.threadID == currSource.threadID) {
                prevPO = currSource;
                break;
            }
        }
		if (prevPO == null) {
			Util.log("Event is not the first event by the thread, but doesn't have any PO edges to previous events");
			return false;
		}
		if (prevPO.eventNumber != prev.eventNumber) {
            Util.log("Event " + prevPO.eventNumber + " by T" + prevPO.threadID + " is missing from reordered trace");
            return false;
        }
        return true;
	}
}

//class VolNode extends EventNode {
//	final boolean isWrite;
//	final String fieldName;
//	
//	VolNode(long eventNumber, int exampleNumber, int threadID, boolean isWrite, String fieldName, AcqRelNode currentCriticalSection) {
//		super(eventNumber, exampleNumber, threadID, currentCriticalSection);
//		this.isWrite = isWrite;
//		this.fieldName = fieldName;
//	}
//	
//	boolean isWrite() {
//		return isWrite;
//	}
//	
//	String getFieldName() {
//		return fieldName;
//	}
//	
//	@Override
//	public String getNodeLabel(){
//		return "vol " + (isWrite() ? "wr" : "rd") + "("+ getFieldName() +") by T"+ threadID;
//	}
//}

/** An instance of RdWr Node can represent multiple consecutive read-write events that aren't interrupted by incoming (outgoing are impossible) WDC edges */
class RdWrNode extends EventNode {
/*
	static final HashMap<RdWrNode,Boolean> isWriteMap;
	static final HashMap<RdWrNode,String> fieldNameMap;
	static final HashMap<RdWrNode,Integer> varIdMap;
*/

	RdWrNode(long eventNumber, long exampleNumber, int threadID, AcqRelNode currentCriticalSection) {
		super(eventNumber, exampleNumber, threadID, currentCriticalSection);
	}

	boolean isWrite() {
		return false; // TODO: this is a confusing answer when debugging info is off
	}
	
	String getFieldName() {
		return "?";
	}

	/** Called when a merge is about to happen, only if DEBUG_ACCESS_INFO is set. To be overridden by subclasses. */
	void mergeWithPriorHook(RdWrDebugNode prior) {}

	/** Can combine two consecutive write/read nodes that have the same VC */
	RdWrNode tryToMergeWithPrior() {
		if (this.sourceOrSources instanceof EventNode) {
			EventNode priorEventNode = (EventNode)this.sourceOrSources;
			if (priorEventNode instanceof RdWrNode) {
				// If a non-PO outgoing edge has already been created from the prior node, then let's not try to do merging.
				synchronized (priorEventNode) {
					if (priorEventNode.sinkOrSinks instanceof EventNode && priorEventNode.threadID == this.threadID) {
						if (DEBUG_ACCESS_INFO) {
							mergeWithPriorHook((RdWrDebugNode) priorEventNode);
						}
//						if (VERBOSE_GRAPH) Assert.assertTrue(priorEventNode.threadID == this.threadID, this.getNodeLabel() + " | " + priorEventNode.getNodeLabel());
						EventNode.removeEdge(priorEventNode, this);
//						((RdWrNode)priorEventNode).lastEventNumber = this.eventNumber;
						//The first event, eventOne, will update it's eventNumber here. So eventOne can not reliably checked for eventNumber == 1
						// Mike says: Actually, I don't think that should happen.
						//((RdWrNode)priorEventNode).eventNumber = this.eventNumber;
						return (RdWrNode)priorEventNode;
					}
				}
			}
		}
		return this;
	}
	
	@Deprecated
	boolean tryToMergeNodeWithSuccessor() {
		if (this.sinkOrSinks instanceof EventNode) {
			EventNode succ = (EventNode)this.sinkOrSinks;
			if (VERBOSE_GRAPH) Assert.assertTrue(this.threadID == succ.threadID);
			if (succ instanceof RdWrNode &&
			    succ.sourceOrSources instanceof EventNode) {
				EventNode.removeEdge(this, succ);
				for (EventNode succSucc : succ.sinkOrSinks) {
					EventNode.addEdge(this, succSucc);
				}
//				((RdWrNode)this).lastEventNumber = succ.eventNumber;
				((RdWrNode)this).eventNumber = succ.eventNumber;
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String toString() {
//		if (eventNumber != lastEventNumber) {
//			return "[" + super.toString() + "-" + String.valueOf(lastEventNumber) + "]";
//		}
		return super.toString();
	}
	
	@Override
	public String getNodeLabel(){
		return "rd(?) by T"+ threadID;
	}
}

/* RdWrNode with debug info attached */
class RdWrDebugNode extends RdWrNode {
	static class Access {
		public final String name;
		public final ShadowVar var;
		public final boolean isWrite;
		public RdWrDebugNode inEvent;

		Access(String name, ShadowVar var, boolean isWrite, RdWrDebugNode inEvent) {
			this.name = name;
			this.var = var;
			this.isWrite = isWrite;
			this.inEvent = inEvent;
		}

		public long eventNumber() {
			return inEvent.eventNumber;
		}

		@Override
		public String toString() {
			return (isWrite ? "wr(" : "rd(") + name + ")";
		}
	}

	/* Due to merging, one RdWrdNode may contain multiple accesses. The vectors here contain the access
	 * information, in the order they were in the trace prior to merging. */
	private final Vector<Access> accesses = new Vector<>();

	public Vector<Access> getAccesses() {
		return accesses;
	}

	RdWrDebugNode(long eventNumber, long exampleNumber, boolean isWrite, String fieldName, ShadowVar var, int threadID, AcqRelNode currentCriticalSection) {
		super(eventNumber, exampleNumber, threadID, currentCriticalSection);
		accesses.add(new Access(fieldName, var, isWrite, this));
	}

	void mergeWithPriorHook(RdWrDebugNode prior) {
		// After merging with prior, the prior event will be the one that is left in the graph
		for (Access acc : accesses) {
			acc.inEvent = prior;
		}
		prior.accesses.addAll(accesses);
	}

	@Override
	public String getNodeLabel() {
		StringBuilder sb = new StringBuilder();
		for (Access acc : accesses) {
			sb.append(acc.isWrite ? "wr(" : "rd(");
			sb.append(acc.name);
			sb.append(") ");
		}
		sb.append("by T");
		sb.append(threadID);
		return sb.toString();
	}
}





class AcqRelNode extends EventNode {
	final ShadowLock shadowLock;
	//final int threadID;
	final boolean isAcquire;
	AcqRelNode otherCriticalSectionNode;
	
	AcqRelNode(long eventNumber, long exampleNumber, ShadowLock shadowLock, int threadID, boolean isAcquire, AcqRelNode currentCriticalSection) {
		super(eventNumber, exampleNumber, threadID, currentCriticalSection);
		this.shadowLock = shadowLock;
		//this.thr_id = threadID;
		this.isAcquire = isAcquire;
	}

	public boolean isAcquire() {
		return isAcquire;
	}

	public AcqRelNode getOtherCriticalSectionNode() {
		return otherCriticalSectionNode;
	}
	
	public ShadowThread getShadowThread() {
		return ShadowThread.get(threadID);
	}
	
	@Override
	public String getNodeLabel(){
		return (isAcquire ? "acq" : "rel") + "(" + Util.objectToIdentityString(shadowLock.getLock()) + ") by T" + threadID;
	}
}

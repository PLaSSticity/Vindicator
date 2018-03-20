package tools.wdc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import acme.util.Util;
import rr.meta.MethodInfo;
import rr.meta.SourceLocation;

class StaticRace {
	HashSet<SourceLocation> locations; // Might contain just one location, which means the location races with itself
	final RdWrNode firstNode;
	final RdWrNode secondNode;
	final RaceType raceType;
	final MethodInfo firstNodeMI;
	final MethodInfo secondNodeMI;
	
	StaticRace(SourceLocation one, SourceLocation another) {
		locations = new HashSet<SourceLocation>();
		locations.add(one);
		locations.add(another);
		this.firstNode = null;
		this.secondNode = null;
		this.raceType = null;
		this.firstNodeMI = null;
		this.secondNodeMI = null;
	}
	
	StaticRace(SourceLocation one, SourceLocation another, RdWrNode firstNode, RdWrNode secondNode, RaceType raceType, MethodInfo firstNodeMI, MethodInfo secondNodeMI) {
		locations = new HashSet<SourceLocation>();
		locations.add(one);
		locations.add(another);
		this.firstNode = firstNode;
		this.secondNode = secondNode;
		this.raceType = raceType;
		this.firstNodeMI = firstNodeMI;
		this.secondNodeMI = secondNodeMI;
	}
	
	@Override
	public boolean equals(Object o) {
		return this.locations.equals(((StaticRace)o).locations);
	}
	
	@Override
	public int hashCode() {
		return locations.hashCode();
	}
	
	@Override
	public String toString() {
		Iterator<SourceLocation> iter = locations.iterator();
		SourceLocation first = iter.next();
		return first + " -> " + (iter.hasNext() ? iter.next() : first); // Handle possibility of location racing with itself
	}
	
	public String description() {
		Iterator<SourceLocation> iter = locations.iterator();
		SourceLocation first = iter.next();
		String firstClass = (firstNodeMI==null ? "null methodInfo" : firstNodeMI.getOwner().getName());
		String firstMethod = (firstNodeMI==null ? "null methodInfo" : firstNodeMI.getName());
		SourceLocation second = (iter.hasNext() ? iter.next() : first);
		String secondClass = (secondNodeMI==null ? "null methodInfo" : secondNodeMI.getOwner().getName());
		String secondMethod = (secondNodeMI==null ? "null methodInfo" : secondNodeMI.getName());
		return "(" + firstClass + ":" + firstMethod + ":" + first + " -> " + secondClass + ":" + secondMethod + ":" + second + ")";
	}
	
	static ConcurrentLinkedQueue<StaticRace> wdcRaces = new ConcurrentLinkedQueue<StaticRace>();
	static HashMap<RaceType,HashMap<StaticRace,Integer>> staticRaceMap = new HashMap<RaceType,HashMap<StaticRace,Integer>>();

	static void addRace(StaticRace staticRace, RaceType type) {
		HashMap<StaticRace,Integer> counts = staticRaceMap.get(type);
		if (counts == null) {
			counts = new HashMap<StaticRace,Integer>();
			staticRaceMap.put(type, counts);
		}
		Integer count = counts.get(staticRace);
		if (count == null) {
			counts.put(staticRace, 1);
		} else {
			counts.put(staticRace, count.intValue() + 1);
		}
	}
	
	static void reportRaces() {
		for (RaceType type : RaceType.values()) {
			if (type.isWDCRace()) { // ignore RaceType.WDCOrdered
				//Static Count
				int race_count = getStaticRaceCount(RaceType.HBRace);
				if (type != RaceType.HBRace) {
					race_count += getStaticRaceCount(RaceType.WCPRace);
					if (type == RaceType.WDCRace) {
						race_count += getStaticRaceCount(RaceType.WDCRace);
					}
				}
				Util.println(race_count + " statically unique " + type.toString() + "(s)");
				//Dynamic Count
				race_count = getDynamicRaceCount(RaceType.HBRace);
				if (type != RaceType.HBRace) {
					race_count += getDynamicRaceCount(RaceType.WCPRace);
					if (type == RaceType.WDCRace) {
						race_count += getDynamicRaceCount(RaceType.WDCRace);
					}
				}
				Util.println(race_count + " dynamic " + type.toString() + "(s)");
			}
			//reportRaces(staticRaceMap.get(type));
		}
	}
	
	static void reportRaces(HashMap<StaticRace,Integer> staticRaces) {
		for (StaticRace staticRace : staticRaces.keySet()) {
			Util.println(staticRace + " (count = " + staticRaces.get(staticRace) + ")");
		}
	}
	
	static int getStaticRaceCount(RaceType type) {
		int race_count = 0;
		if (type == RaceType.HBRace) {
			race_count = staticRaceMap.get(type) == null ? 0 : staticRaceMap.get(type).size();
		} else if (staticRaceMap.get(type) != null) {
			if (type == RaceType.WCPRace) {
				for (StaticRace race : staticRaceMap.get(type).keySet()) {
					if (staticRaceMap.get(RaceType.HBRace) == null) {
						race_count++;
					} else if (!staticRaceMap.get(RaceType.HBRace).containsKey(race)) {
						race_count++;
					}
				}
			} else {
				for (StaticRace race : staticRaceMap.get(type).keySet()) {
					if (staticRaceMap.get(RaceType.HBRace) == null && staticRaceMap.get(RaceType.WCPRace) == null) {
						race_count++;
					} else if (!((staticRaceMap.get(RaceType.HBRace) != null && staticRaceMap.get(RaceType.HBRace).containsKey(race)) || 
							(staticRaceMap.get(RaceType.WCPRace) != null && staticRaceMap.get(RaceType.WCPRace).containsKey(race)))) {
						race_count++;
					}
				}
			}
		}
		return race_count;
	}
	
	static int getDynamicRaceCount(RaceType type) {
		int race_count = 0;
		if (staticRaceMap.get(type) != null) {
			for (StaticRace race : staticRaceMap.get(type).keySet()) {
				race_count += staticRaceMap.get(type).get(race);
			}
		}
		return race_count;
	}
}

enum RaceType {
	HBRace,
	WCPRace, // but HB ordered
	WDCRace, // but WCP ordered 
	WDCOrdered;
	
	boolean isWDCRace() {
		return this.equals(HBRace) || this.equals(WCPRace) || this.equals(WDCRace);
	}
	
	boolean isWCPRace() {
		return this.equals(HBRace) || this.equals(WCPRace);
	}
	
	boolean isHBRace() {
		return this.equals(HBRace);
	}
	@Override
	public String toString() {
		switch (this) {
		case HBRace: return "HB-race";
		case WCPRace: return "WCP-race";
		case WDCRace: return "WDC-race";
		case WDCOrdered: return "WDC-ordered";
		default: return null;
		}
	}
	String relation() {
		switch (this) {
		case HBRace: return "HB";
		case WCPRace: return "WCP";
		case WDCRace: return "WDC";
		default: return null;
		}
	}
}

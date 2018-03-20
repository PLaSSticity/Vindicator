package tools.wdc;

/** Vector clock with an event node */
class CVE extends CV {
	
	EventNode eventNode;
	
	public CVE(CV cv, EventNode eventNode) {
		super(cv);
		this.eventNode = eventNode;
	}
	
	public void setEventNode(EventNode eventNode) {
		this.eventNode = eventNode;
	}

	@Override
	public String toString() {
		return super.toString() + " (event " + eventNode + ")";
	}

}

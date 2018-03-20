package tools.gcp;

class ThrModifier extends Modifier {
	int uniqueCount = -1;
	
	ThrModifier(Type type) {
		super(type);
		if (GCPTool.Verbose == 1) assert type != Type.LS;
	}
	
	ThrModifier(Type type, int count) {
		super(type);
		this.uniqueCount = count;
		if (GCPTool.Verbose == 1) assert type != Type.LS;
	}
}
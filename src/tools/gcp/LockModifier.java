package tools.gcp;

class LockModifier extends Modifier {
	private final int lockVersion;
	private Lock lockInstance;

	LockModifier(Type type, Lock lockInstance, int lockVersion) {
		super(type);
		if (GCPTool.Verbose == 1) assert type == Type.HB || type == Type.LS;
		this.lockVersion = lockVersion;
		this.lockInstance = lockInstance;
	}
	
	LockModifier(Type type, Lock lockInstance) {
		super(type);
		if (GCPTool.Verbose == 1) assert type == Type.CP;
		this.lockVersion = -1;
		this.lockInstance = lockInstance;
	}
	
	int getLockVersion() {
		return lockVersion;
	}
	
	Lock getLockInstance() {
		return lockInstance;
	}
	
	public String toString() {
		if (type == Type.CP) {
			return super.toString();
		}
		return super.toString() + "(" + lockVersion + ")";
	}
}
package tools.gcp;

abstract class Modifier {
	final Type type;

	Modifier(Type type) {
		this.type = type;
	}

	public String toString() {
		String s = null;
		switch (type) {
		case HB: s = "_HB"; break;
		case LS: s = "_LS"; break;
		case CP: s = "_CP"; break;
		default: assert false;
		}
		return s;
	}
}
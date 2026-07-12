package info.openrocket.core.aerodynamics.physicsaero.fin;

public enum LeadingEdgeClassification {
	SUPERSONIC_LEADING_EDGE, NEAR_SONIC_LEADING_EDGE, SUBSONIC_LEADING_EDGE;

	public static LeadingEdgeClassification classify(double normalMach, double epsilonMach) {
		if (!Double.isFinite(normalMach) || normalMach < 0 || epsilonMach < 0) {
			throw new IllegalArgumentException("invalid leading-edge classification input");
		}
		if (normalMach > 1.0 + epsilonMach) return SUPERSONIC_LEADING_EDGE;
		if (normalMach < 1.0 - epsilonMach) return SUBSONIC_LEADING_EDGE;
		return NEAR_SONIC_LEADING_EDGE;
	}
}

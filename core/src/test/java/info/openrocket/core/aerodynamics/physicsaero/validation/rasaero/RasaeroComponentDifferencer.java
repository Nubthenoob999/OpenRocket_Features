package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

public final class RasaeroComponentDifferencer {
	public Increment difference(Cumulative upstream, Cumulative downstream, double normalForceThreshold) {
		double ca = downstream.ca() - upstream.ca(), cn = downstream.cn() - upstream.cn(), cm = downstream.cm() - upstream.cm();
		Double cp = Math.abs(cn) <= normalForceThreshold ? null : downstream.xReferenceM() - cm * downstream.referenceLengthM() / cn;
		return new Increment(ca, cn, cm, cp);
	}
	public record Cumulative(double ca, double cn, double cm, double xReferenceM, double referenceLengthM) {
		public Cumulative { if (referenceLengthM <= 0) throw new IllegalArgumentException(); }
		public static Cumulative fromCp(double ca, double cn, double cpM, double xReferenceM, double referenceLengthM) {
			return new Cumulative(ca, cn, -cn * (cpM - xReferenceM) / referenceLengthM, xReferenceM, referenceLengthM);
		}
	}
	public record Increment(double ca, double cn, double cm, Double cpM) {}
}

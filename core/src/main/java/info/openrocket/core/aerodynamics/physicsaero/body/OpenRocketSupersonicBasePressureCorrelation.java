package info.openrocket.core.aerodynamics.physicsaero.body;

/**
 * Historical OpenRocket supersonic blunt-base correlation extracted from
 * BarrowmanDragCalculator: -Cp,b = 0.25/M. It is independently owned here and
 * does not import the legacy ROM implementation.
 */
public final class OpenRocketSupersonicBasePressureCorrelation implements BasePressureCorrelation {
	public static final String METHOD_ID = "OPENROCKET_SUPERSONIC_BASE_CP_V1";
	@Override public String methodId() { return METHOD_ID; }
	@Override public String source() { return "OpenRocket BarrowmanDragCalculator.calculateBaseCD: |Cp,b|=0.25/M"; }
	@Override public Validity validity(double mach, boolean powered) {
		if (powered) return new Validity(false, "POWERED_BASE_MODEL_NOT_IMPLEMENTED", Double.NaN);
		if (mach < 1 || mach > 5) return new Validity(false, "OUTSIDE_SUPERSONIC_BASE_CORRELATION_RANGE", Double.NaN);
		return new Validity(true, "VALID_UNPOWERED_BLUNT_BASE", 0.20);
	}
	@Override public double basePressureCoefficient(double mach, double gamma) {
		Validity validity = validity(mach, false); if (!validity.valid()) throw new IllegalArgumentException(validity.reason());
		return -0.25 / mach;
	}
}

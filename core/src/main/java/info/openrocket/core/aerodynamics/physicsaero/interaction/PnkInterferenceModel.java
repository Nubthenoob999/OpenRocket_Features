package info.openrocket.core.aerodynamics.physicsaero.interaction;

/** Applies carryover factors to isolated loads; result is the increment, not a duplicate full load. */
public final class PnkInterferenceModel {
	public static final String METHOD_ID = "PNK_NACA_1307_TABLE_V1";
	private final PnkFactorTable table;
	private final PnkValidityDomain domain;
	public PnkInterferenceModel() { this(new PnkFactorTable(), new PnkValidityDomain(0, 5, 0, 0.8)); }
	public PnkInterferenceModel(PnkFactorTable table, PnkValidityDomain domain) { this.table = table; this.domain = domain; }
	public Result evaluate(double mach, double radiusOverSemispan, double isolatedFinNormalN, double isolatedBodyNormalN) {
		if (!domain.contains(mach, radiusOverSemispan)) return new Result(false, 0, 0, 0, "OUTSIDE_PNK_DOMAIN", METHOD_ID);
		PnkFactorTable.Factors factors = table.interpolate(radiusOverSemispan);
		double combined = factors.wingBodyFactor() * isolatedFinNormalN + factors.bodyWingFactor() * isolatedBodyNormalN;
		double increment = combined - isolatedFinNormalN - isolatedBodyNormalN;
		return new Result(true, combined, increment, factors.wingBodyFactor(), "NACA_1307_BOUNDED_INTERPOLATION", METHOD_ID);
	}
	public record Result(boolean valid, double combinedNormalN, double interferenceIncrementN,
			double wingBodyFactor, String provenance, String methodId) {}
}

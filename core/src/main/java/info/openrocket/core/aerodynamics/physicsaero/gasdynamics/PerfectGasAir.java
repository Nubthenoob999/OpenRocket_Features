package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

/** Calorically perfect air; Sutherland constants follow the NIST reference form. */
public record PerfectGasAir(double gammaValue, double gasConstant, double viscosityReferencePaS,
		double temperatureReferenceK, double sutherlandK) implements ThermodynamicModel {
	public PerfectGasAir {
		if (gammaValue <= 1 || gasConstant <= 0 || viscosityReferencePaS <= 0
				|| temperatureReferenceK <= 0 || sutherlandK <= 0) throw new IllegalArgumentException("invalid perfect-gas constants");
	}
	public PerfectGasAir() { this(1.4, 287.05287, 1.716e-5, 273.15, 110.4); }
	@Override public double gamma(double temperatureK) { return gammaValue; }
	@Override public double viscosity(double temperatureK) {
		if (temperatureK <= 0) throw new IllegalArgumentException("temperature must be positive");
		return viscosityReferencePaS * Math.pow(temperatureK / temperatureReferenceK, 1.5)
				* (temperatureReferenceK + sutherlandK) / (temperatureK + sutherlandK);
	}
}

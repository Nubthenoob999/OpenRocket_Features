package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;

/** Modified-Newtonian detached/blunt fallback using Rayleigh-Pitot stagnation pressure. */
public final class ModifiedNewtonianPressure {
	public static final String METHOD_ID = "MODIFIED_NEWTONIAN_FALLBACK_V1";
	public double stagnationPressureCoefficient(GasState freestream, ThermodynamicModel model) {
		if (freestream.mach() <= 1) throw new GasDynamicsException("modified Newtonian fallback requires supersonic flow");
		ShockSolution shock = NormalShockCalculator.solve(freestream, model);
		double q = 0.5 * freestream.densityKgM3() * freestream.velocityMS() * freestream.velocityMS();
		return (shock.downstreamTotal().pressurePa() - freestream.pressurePa()) / q;
	}
	public double pressureCoefficient(GasState freestream, double surfaceAngleRad, ThermodynamicModel model) {
		if (!Double.isFinite(surfaceAngleRad)) throw new GasDynamicsException("non-finite surface angle");
		double sine = Math.sin(Math.max(0, Math.min(Math.PI / 2, Math.abs(surfaceAngleRad))));
		return stagnationPressureCoefficient(freestream, model) * sine * sine;
	}
}

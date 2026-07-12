package info.openrocket.core.aerodynamics.physicsaero.flow;

import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;

/** Complete inviscid edge state exported for the later boundary-layer marcher. */
public record SurfaceState(double xM, double radiusM, GasState staticState, TotalState totalState,
		double flowAngleRad, double pressureCoefficient, double speedOfSoundMS,
		double dynamicViscosityPaS, String methodId) {
	public SurfaceState {
		if (staticState == null || totalState == null || methodId == null || methodId.isBlank()
				|| !Double.isFinite(xM + radiusM + flowAngleRad + pressureCoefficient + speedOfSoundMS + dynamicViscosityPaS)
				|| radiusM < 0 || speedOfSoundMS <= 0 || dynamicViscosityPaS <= 0) {
			throw new IllegalArgumentException("invalid surface state");
		}
	}
	public static SurfaceState of(double xM, double radiusM, GasState state, TotalState total,
			double flowAngleRad, double cp, ThermodynamicModel model, String methodId) {
		return new SurfaceState(xM, radiusM, state, total, flowAngleRad, cp,
				model.speedOfSound(state.temperatureK()), model.viscosity(state.temperatureK()), methodId);
	}
}

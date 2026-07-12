package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import info.openrocket.core.aerodynamics.physicsaero.thermal.WallThermalBoundary;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionMode;

public record BoundaryLayerConfiguration(TransitionMode transitionMode, double turbulencePercent,
		double transitionBlendLengthM, double intermittencyExponent, WallThermalBoundary wallMode,
		double gamma, double prandtl, double minimumVelocityMS, double totalStateRelativeTolerance) {
	public static BoundaryLayerConfiguration defaults() { return new BoundaryLayerConfiguration(TransitionMode.NATURAL, 0.2, 0.02, 2.0, WallThermalBoundary.ADIABATIC, 1.4, 0.72, 1e-3, 2e-3); }
	public BoundaryLayerConfiguration {
		if (transitionMode == null || wallMode == null || turbulencePercent < 0 || transitionBlendLengthM <= 0
				|| intermittencyExponent <= 0 || gamma <= 1 || prandtl <= 0 || minimumVelocityMS <= 0 || totalStateRelativeTolerance <= 0)
			throw new IllegalArgumentException("invalid boundary-layer configuration");
	}
}

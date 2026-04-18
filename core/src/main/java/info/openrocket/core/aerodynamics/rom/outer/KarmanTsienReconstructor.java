package info.openrocket.core.aerodynamics.rom.outer;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

public class KarmanTsienReconstructor implements OuterFlowReconstructor {
	@Override
	public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
		double radius = Math.max(1.0e-6, geometry.radiusAt(seed.getX()));
		double drdx = geometry.areaSlopeAt(seed.getX()) / Math.max(1.0e-6, 2.0 * Math.PI * radius);
		double localIncidence = flowState.getAngleOfAttackRad() + Math.atan(drdx);
		double cpIncompressible = clamp(2.0 * localIncidence, -1.20, 1.20);
		double mach = Math.max(0.0, Math.min(0.79, flowState.getMach()));
		double cp = mach < 0.55
				? GasDynamics.prandtlGlauertCp(cpIncompressible, mach)
				: GasDynamics.karmanTsienCp(cpIncompressible, mach);
		cp = clamp(cp, -1.50, 1.50);
		double edgeVelocity = flowState.getVelocity() * Math.sqrt(Math.max(0.05, 1.0 - 0.5 * cp));
		double edgeMach = edgeVelocity / Math.max(1e-6, flowState.getSpeedOfSound());
		return new EdgeState(edgeVelocity, edgeMach, cp, localIncidence, -drdx * flowState.getVelocity(), true);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

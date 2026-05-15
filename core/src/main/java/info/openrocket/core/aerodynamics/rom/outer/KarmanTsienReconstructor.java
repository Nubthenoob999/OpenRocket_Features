package info.openrocket.core.aerodynamics.rom.outer;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.flow.MachTransitionMap;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

public class KarmanTsienReconstructor implements OuterFlowReconstructor {
	private static final double PRESONIC_CP_GAIN = 1.18;
	private static final double PRESONIC_CP_INCREMENT = 0.08;

	@Override
	public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
		double radius = Math.max(1.0e-6, geometry.radiusAt(seed.getX()));
		double drdx = geometry.areaSlopeAt(seed.getX()) / Math.max(1.0e-6, 2.0 * Math.PI * radius);
		double localIncidence = flowState.getAngleOfAttackRad() + Math.atan(drdx);
		double cpIncompressible = clamp(2.0 * localIncidence, -1.20, 1.20);
		double mach = Math.max(0.0, flowState.getMach());
		double cp = switch (MachTransitionMap.band(mach)) {
			case INCOMPRESSIBLE -> cpIncompressible;
			case COMPRESSIBLE_SUBSONIC -> compressibleSubsonicCp(cpIncompressible, mach);
			case PRESONIC_TRANSONIC -> presonicCp(cpIncompressible, mach);
			case SUPERSONIC, HYPERSONIC_LEANING -> presonicCp(cpIncompressible, MachTransitionMap.M_PRESONIC_TARGET);
		};
		cp = clamp(cp, -1.50, 1.50);
		return edgeState(flowState, cp, localIncidence, drdx);
	}

	private static double compressibleSubsonicCp(double cpIncompressible, double mach) {
		double safeMach = Math.min(Math.max(0.0, mach), MachTransitionMap.M_SUBSONIC_END);
		double corrected = safeMach < 0.55
				? cpIncompressible / MachTransitionMap.finitePrandtlGlauertBeta(safeMach)
				: GasDynamics.karmanTsienCp(cpIncompressible, safeMach);
		double weight = MachTransitionMap.compressibilityWeight(safeMach);
		return lerp(cpIncompressible, corrected, weight);
	}

	private static double presonicCp(double cpIncompressible, double mach) {
		double anchorCp = compressibleSubsonicCp(cpIncompressible, MachTransitionMap.M_SUBSONIC_END);
		double sign = Math.signum(anchorCp);
		double incidenceScale = Math.min(1.0, Math.abs(cpIncompressible) / 0.40);
		double targetCp = anchorCp * PRESONIC_CP_GAIN + sign * PRESONIC_CP_INCREMENT * incidenceScale;
		double weight = MachTransitionMap.presonicWeight(Math.min(mach, MachTransitionMap.M_PRESONIC_TARGET));
		return lerp(anchorCp, clamp(targetCp, -1.50, 1.50), weight);
	}

	private static EdgeState edgeState(FlowState flowState, double cp, double localIncidence, double drdx) {
		double edgeVelocity = flowState.getVelocity() * Math.sqrt(Math.max(0.05, 1.0 - 0.5 * cp));
		double edgeMach = edgeVelocity / Math.max(1e-6, flowState.getSpeedOfSound());
		return new EdgeState(edgeVelocity, edgeMach, cp, localIncidence, -drdx * flowState.getVelocity(), true);
	}

	private static double lerp(double left, double right, double weight) {
		return left + weight * (right - left);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

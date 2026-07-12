package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.util.Coordinate;

/** Geometry and wall data paired with the Phase-1 {@link SurfaceState}; all values are SI. */
public record BoundaryLayerStation(double sM, double deltaSM, Coordinate positionM, double radiusM,
		double radiusGradient, double curvaturePerM, double streamwiseVelocityMS, double crossflowVelocityMS,
		double velocityGradientPerS, double pressureGradientPaM, double wallTemperatureK,
		double roughnessM, boolean forcedTrip, String event, SurfaceState edgeState,
		Coordinate streamwiseTangent, double areaWidthM) {
	public BoundaryLayerStation {
		if (positionM == null || edgeState == null || streamwiseTangent == null || event == null
				|| !finite(sM, deltaSM, radiusM, radiusGradient, curvaturePerM, streamwiseVelocityMS,
				crossflowVelocityMS, velocityGradientPerS, pressureGradientPaM, wallTemperatureK, roughnessM, areaWidthM)
				|| sM < 0 || deltaSM < 0 || radiusM < 0 || streamwiseVelocityMS < 0
				|| wallTemperatureK <= 0 || roughnessM < 0 || areaWidthM <= 0
				|| streamwiseTangent.length() == 0) throw new IllegalArgumentException("invalid boundary-layer station");
		positionM = new Coordinate(positionM.x, positionM.y, positionM.z);
		double n = streamwiseTangent.length();
		streamwiseTangent = new Coordinate(streamwiseTangent.x / n, streamwiseTangent.y / n, streamwiseTangent.z / n);
	}
	public double densityKgM3() { return edgeState.staticState().densityKgM3(); }
	public double pressurePa() { return edgeState.staticState().pressurePa(); }
	public double temperatureK() { return edgeState.staticState().temperatureK(); }
	public double mach() { return edgeState.staticState().mach(); }
	public double viscosityPaS() { return edgeState.dynamicViscosityPaS(); }
	public double kinematicViscosityM2S() { return viscosityPaS() / densityKgM3(); }
	private static boolean finite(double... values) { for (double v : values) if (!Double.isFinite(v)) return false; return true; }
}

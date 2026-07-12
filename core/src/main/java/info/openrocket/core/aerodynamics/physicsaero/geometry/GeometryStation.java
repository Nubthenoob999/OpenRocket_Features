package info.openrocket.core.aerodynamics.physicsaero.geometry;

public record GeometryStation(double xM, double radiusM, double slope, double secondDerivative) {
	public GeometryStation {
		if (!Double.isFinite(xM + radiusM + slope + secondDerivative) || radiusM < 0) throw new IllegalArgumentException("invalid geometry station");
	}
	public double areaM2() { return Math.PI * radiusM * radiusM; }
	public double tangentAngleRad() { return Math.atan(slope); }
	public double meridionalCurvature() { return secondDerivative / Math.pow(1 + slope * slope, 1.5); }
}

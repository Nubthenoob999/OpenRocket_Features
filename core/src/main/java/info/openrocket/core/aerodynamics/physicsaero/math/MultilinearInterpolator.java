package info.openrocket.core.aerodynamics.physicsaero.math;

public final class MultilinearInterpolator {
	private MultilinearInterpolator() {}
	public static double trilinear(double[] corners, double tx, double ty, double tz) {
		if (corners.length != 8 || tx < 0 || tx > 1 || ty < 0 || ty > 1 || tz < 0 || tz > 1) throw new IllegalArgumentException("invalid trilinear input");
		double c00 = mix(corners[0], corners[4], tx), c01 = mix(corners[1], corners[5], tx);
		double c10 = mix(corners[2], corners[6], tx), c11 = mix(corners[3], corners[7], tx);
		return mix(mix(c00, c10, ty), mix(c01, c11, ty), tz);
	}
	private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}

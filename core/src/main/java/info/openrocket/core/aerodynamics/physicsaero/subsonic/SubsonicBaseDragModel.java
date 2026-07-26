package info.openrocket.core.aerodynamics.physicsaero.subsonic;

/**
 * Historical OpenRocket/extended-Barrowman unpowered subsonic base correlation.
 * The positive base-drag coefficient is 0.12 + 0.13 M^2; this class returns the
 * corresponding negative base pressure coefficient.
 */
public final class SubsonicBaseDragModel {
	public static final String METHOD_ID = "OPENROCKET_SUBSONIC_BASE_CP_V2";

	public double basePressureCoefficient(double mach, double reynoldsDiameter) {
		if (mach < 0 || mach >= 1 || reynoldsDiameter <= 0) {
			throw new IllegalArgumentException();
		}
		return -Math.min(0.3, 0.12 + 0.13 * mach * mach);
	}
}

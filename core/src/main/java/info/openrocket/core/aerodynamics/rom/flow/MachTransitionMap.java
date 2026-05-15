package info.openrocket.core.aerodynamics.rom.flow;

public final class MachTransitionMap {
	public static final double M_INCOMPRESSIBLE_END = 0.30;
	public static final double M_SUBSONIC_END = 0.80;
	public static final double M_PRESONIC_END = 1.00;
	public static final double M_PRESONIC_TARGET = 0.98;

	public enum RegimeBand {
		INCOMPRESSIBLE,
		COMPRESSIBLE_SUBSONIC,
		PRESONIC_TRANSONIC,
		SUPERSONIC,
		HYPERSONIC_LEANING
	}

	private MachTransitionMap() {
	}

	public static RegimeBand band(double mach) {
		double boundedMach = sanitizeMach(mach);
		if (boundedMach < M_INCOMPRESSIBLE_END) {
			return RegimeBand.INCOMPRESSIBLE;
		}
		if (boundedMach < M_SUBSONIC_END) {
			return RegimeBand.COMPRESSIBLE_SUBSONIC;
		}
		if (boundedMach < M_PRESONIC_END) {
			return RegimeBand.PRESONIC_TRANSONIC;
		}
		if (boundedMach < 3.00) {
			return RegimeBand.SUPERSONIC;
		}
		return RegimeBand.HYPERSONIC_LEANING;
	}

	public static double compressibilityWeight(double mach) {
		double t = (sanitizeMach(mach) - M_INCOMPRESSIBLE_END) / (M_SUBSONIC_END - M_INCOMPRESSIBLE_END);
		return cubicHermiteUnit(t);
	}

	public static double presonicWeight(double mach) {
		double t = (sanitizeMach(mach) - M_SUBSONIC_END) / (M_PRESONIC_END - M_SUBSONIC_END);
		return cubicHermiteUnit(t);
	}

	public static double cubicHermiteUnit(double t) {
		double bounded = clamp(t, 0.0, 1.0);
		return bounded * bounded * (3.0 - 2.0 * bounded);
	}

	public static double finitePrandtlGlauertBeta(double mach) {
		double cappedMach = Math.min(Math.abs(sanitizeMach(mach)), M_SUBSONIC_END);
		return Math.sqrt(Math.max(0.01, 1.0 - cappedMach * cappedMach));
	}

	private static double sanitizeMach(double mach) {
		if (!Double.isFinite(mach)) {
			return 0.0;
		}
		return Math.max(0.0, mach);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

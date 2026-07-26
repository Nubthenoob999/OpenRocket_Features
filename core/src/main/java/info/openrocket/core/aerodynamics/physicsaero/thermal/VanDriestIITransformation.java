package info.openrocket.core.aerodynamics.physicsaero.thermal;

/**
 * Van Driest II turbulent skin-friction transformation.
 *
 * <p>Adapted from the implementation in AidanSYu/openrocketsupersonic,
 * {@code supersonic-aero-dev}, and corrected against E. J. Hopkins,
 * NASA TN D-6945 (1972), equations 1-18.  The viscosity part of the
 * momentum-thickness transformation uses Sutherland's law consistently
 * with the rest of the physics-aerodynamics package; no additional Keyes
 * factor is applied.</p>
 */
public final class VanDriestIITransformation {
	public static final String METHOD_ID = "van-driest-ii-hopkins-tnd6945-v1";
	public static final double RECOVERY_FACTOR = 0.88;

	private static final double MINIMUM_REYNOLDS = 1_000;
	private static final double LOW_MACH_LIMIT = 1.0e-6;
	private static final double SCHOENHERR_TOLERANCE = 1.0e-12;
	private static final int MAXIMUM_ITERATIONS = 50;

	private final SutherlandViscosity viscosity = new SutherlandViscosity();

	/**
	 * Returns the local compressible skin-friction coefficient.
	 *
	 * @param mach edge Mach number
	 * @param reynoldsX edge length Reynolds number
	 * @param edgeTemperatureK edge static temperature
	 * @param wallTemperatureK wall temperature
	 */
	public double cf(double mach, double reynoldsX, double edgeTemperatureK,
			double wallTemperatureK) {
		validate(mach, reynoldsX, edgeTemperatureK, wallTemperatureK);
		double boundedReynolds = Math.max(MINIMUM_REYNOLDS, reynoldsX);
		if (mach <= LOW_MACH_LIMIT) {
			return localFromAverage(solveSchoenherrCF(boundedReynolds));
		}

		double fc = fc(mach, wallTemperatureK / edgeTemperatureK);
		double fTheta = fTheta(edgeTemperatureK, wallTemperatureK);
		double transformedReynolds = Math.max(MINIMUM_REYNOLDS,
				boundedReynolds * fTheta / fc);
		double transformedAverageCf = solveSchoenherrCF(transformedReynolds);
		return localFromAverage(transformedAverageCf) / fc;
	}

	/**
	 * Ratio form used to retain the pressure-gradient history in a marched
	 * incompressible closure while applying the Van Driest II compressibility
	 * effect.
	 */
	public double compressibilityFactor(double mach, double reynoldsX,
			double edgeTemperatureK, double wallTemperatureK) {
		double incompressible = cf(0, reynoldsX, edgeTemperatureK, wallTemperatureK);
		return cf(mach, reynoldsX, edgeTemperatureK, wallTemperatureK) / incompressible;
	}

	/** Van Driest II transformation function F_c, equations 8 and 12-17. */
	double fc(double mach, double wallToEdgeTemperatureRatio) {
		if (mach < 0 || !(wallToEdgeTemperatureRatio > 0)
				|| !Double.isFinite(mach + wallToEdgeTemperatureRatio)) {
			throw new IllegalArgumentException("invalid Van Driest II transformation state");
		}
		if (mach <= LOW_MACH_LIMIT) {
			return 1;
		}
		double m = 0.2 * mach * mach;
		double f = wallToEdgeTemperatureRatio;
		double a = Math.sqrt(RECOVERY_FACTOR * m / f);
		double b = (1 + RECOVERY_FACTOR * m - f) / f;
		double discriminant = Math.hypot(2 * a, b);
		if (discriminant == 0) {
			return 1;
		}
		double alpha = clamp((2 * a * a - b) / discriminant, -1, 1);
		double beta = clamp(b / discriminant, -1, 1);
		double denominator = Math.asin(alpha) + Math.asin(beta);
		if (Math.abs(denominator) < 1.0e-10) {
			return 1;
		}
		return RECOVERY_FACTOR * m / (denominator * denominator);
	}

	/**
	 * Momentum-thickness transformation F_theta, equation 10, evaluated with
	 * Sutherland viscosity for both edge and wall states.
	 */
	double fTheta(double edgeTemperatureK, double wallTemperatureK) {
		if (!(edgeTemperatureK > 0) || !(wallTemperatureK > 0)
				|| !Double.isFinite(edgeTemperatureK + wallTemperatureK)) {
			throw new IllegalArgumentException("invalid Van Driest II temperature state");
		}
		double viscosityRatio = viscosity.viscosityPaS(edgeTemperatureK)
				/ viscosity.viscosityPaS(wallTemperatureK);
		return viscosityRatio * Math.sqrt(edgeTemperatureK / wallTemperatureK);
	}

	/** Solves the Karman-Schoenherr implicit relation for average C_f. */
	public double solveSchoenherrCF(double reynoldsX) {
		if (!(reynoldsX > 0) || !Double.isFinite(reynoldsX)) {
			throw new IllegalArgumentException("invalid Reynolds number");
		}
		double boundedReynolds = Math.max(MINIMUM_REYNOLDS, reynoldsX);
		double cf = 0.455 / Math.pow(Math.log10(boundedReynolds), 2.58);
		for (int iteration = 0; iteration < MAXIMUM_ITERATIONS; iteration++) {
			double sqrtCf = Math.sqrt(cf);
			double residual = 0.242 / sqrtCf - Math.log10(boundedReynolds * cf);
			double derivative = -0.121 / (cf * sqrtCf)
					- 1 / (cf * Math.log(10));
			double delta = residual / derivative;
			cf = Math.max(1.0e-8, cf - delta);
			if (Math.abs(delta) < SCHOENHERR_TOLERANCE) {
				break;
			}
		}
		return cf;
	}

	/** Converts the average flat-plate coefficient to its local value. */
	public double localFromAverage(double averageCf) {
		if (!(averageCf > 0) || !Double.isFinite(averageCf)) {
			throw new IllegalArgumentException("invalid average skin-friction coefficient");
		}
		return 0.242 * averageCf / (0.242 + 0.8686 * Math.sqrt(averageCf));
	}

	private static void validate(double mach, double reynoldsX, double edgeTemperatureK,
			double wallTemperatureK) {
		if (mach < 0 || !(reynoldsX > 0) || !(edgeTemperatureK > 0)
				|| !(wallTemperatureK > 0)
				|| !Double.isFinite(mach + reynoldsX + edgeTemperatureK + wallTemperatureK)) {
			throw new IllegalArgumentException("invalid Van Driest II state");
		}
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}

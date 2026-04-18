package info.openrocket.core.aerodynamics.rom.math;

/**
 * Compressible gas-dynamics utility methods used by the ROM outer-flow and
 * boundary-layer closures.
 *
 * <p>Primary references: Anderson, Modern Compressible Flow; NACA Report 1135.
 */
public final class GasDynamics {
	private static final double MIN_GAMMA = 1.0;
	private static final double NEWTON_TOLERANCE = 1.0e-9;
	private static final int NEWTON_MAX_STEPS = 8;
	private static final double SHOCK_BISECTION_TOLERANCE = 1.0e-8;
	private static final int SHOCK_BISECTION_MAX_STEPS = 80;

	// Sutherland-law constants for air.
	private static final double SUTHERLAND_REFERENCE_TEMPERATURE_K = 273.15;
	private static final double SUTHERLAND_REFERENCE_VISCOSITY_PA_S = 1.716e-5;
	private static final double SUTHERLAND_CONSTANT_K = 110.4;

	private GasDynamics() {
	}

	public static double prandtlMeyerAngle(double mach, double gamma) {
		validateGamma(gamma);
		if (mach < 1.0) {
			throw new IllegalArgumentException("Prandtl-Meyer angle is only defined for M >= 1");
		}
		if (mach == 1.0) {
			return 0.0;
		}
		double machSquared = mach * mach;
		double root = Math.sqrt(machSquared - 1.0);
		double gammaRatio = Math.sqrt((gamma + 1.0) / (gamma - 1.0));
		double inner = Math.sqrt(((gamma - 1.0) / (gamma + 1.0)) * (machSquared - 1.0));
		return gammaRatio * Math.atan(inner) - Math.atan(root);
	}

	public static double prandtlMeyerMach(double nu, double gamma) {
		validateGamma(gamma);
		if (!Double.isFinite(nu) || nu < 0.0) {
			throw new IllegalArgumentException("Prandtl-Meyer angle must be finite and non-negative");
		}
		if (nu == 0.0) {
			return 1.0;
		}

		double mach = Math.max(1.0001, 1.0 + nu * 1.5);
		for (int i = 0; i < NEWTON_MAX_STEPS; i++) {
			double residual = prandtlMeyerAngle(mach, gamma) - nu;
			if (Math.abs(residual) < NEWTON_TOLERANCE) {
				return mach;
			}
			double step = residual / Math.max(1.0e-9, prandtlMeyerDerivative(mach, gamma));
			mach = Math.max(1.0, mach - step);
		}
		return mach;
	}

	public static double isentropicPressureRatio(double mach, double gamma) {
		validateGamma(gamma);
		double factor = 1.0 + 0.5 * (gamma - 1.0) * mach * mach;
		return Math.pow(factor, -gamma / (gamma - 1.0));
	}

	public static double isentropicTempRatio(double mach, double gamma) {
		validateGamma(gamma);
		return 1.0 / (1.0 + 0.5 * (gamma - 1.0) * mach * mach);
	}

	public static double isentropicDensityRatio(double mach, double gamma) {
		validateGamma(gamma);
		double factor = 1.0 + 0.5 * (gamma - 1.0) * mach * mach;
		return Math.pow(factor, -1.0 / (gamma - 1.0));
	}

	public static double rayleighPitotPressureRatio(double mach, double gamma) {
		validateGamma(gamma);
		if (mach <= 1.0) {
			return 1.0 / isentropicPressureRatio(mach, gamma);
		}
		double normalShockPressure = 1.0 + (2.0 * gamma / (gamma + 1.0)) * (mach * mach - 1.0);
		double mach2Squared = (1.0 + 0.5 * (gamma - 1.0) * mach * mach)
				/ (gamma * mach * mach - 0.5 * (gamma - 1.0));
		double mach2 = Math.sqrt(Math.max(0.0, mach2Squared));
		return normalShockPressure / isentropicPressureRatio(mach2, gamma);
	}

	public static double cpMax(double mach, double gamma) {
		validateGamma(gamma);
		if (mach <= 0.0) {
			return 0.0;
		}
		return (2.0 / (gamma * mach * mach)) * (rayleighPitotPressureRatio(mach, gamma) - 1.0);
	}

	public static double modifiedNewtonianCp(double localInclination, double cpMax) {
		double sine = Math.sin(localInclination);
		return cpMax * sine * sine;
	}

	public static double obliqueShockAngle(double mach, double deflectionAngle, double gamma) {
		validateGamma(gamma);
		if (mach <= 1.0) {
			return Double.NaN;
		}
		double theta = Math.abs(deflectionAngle);
		if (theta == 0.0) {
			return Math.asin(1.0 / mach);
		}
		double thetaMax = maxDeflectionAngle(mach, gamma);
		if (!(theta < thetaMax)) {
			return Double.NaN;
		}

		double lower = Math.asin(1.0 / mach) + 1.0e-8;
		double upper = 0.5 * Math.PI - 1.0e-8;
		for (int i = 0; i < SHOCK_BISECTION_MAX_STEPS; i++) {
			double mid = 0.5 * (lower + upper);
			double residual = thetaBetaMResidual(mach, theta, mid, gamma);
			if (Math.abs(residual) < SHOCK_BISECTION_TOLERANCE) {
				return mid;
			}
			double lowerResidual = thetaBetaMResidual(mach, theta, lower, gamma);
			if (Math.signum(lowerResidual) == Math.signum(residual)) {
				lower = mid;
			} else {
				upper = mid;
			}
		}
		return 0.5 * (lower + upper);
	}

	public static double obliqueShockPressureRatio(double mach, double shockAngle, double gamma) {
		validateGamma(gamma);
		double mn1 = mach * Math.sin(shockAngle);
		return 1.0 + (2.0 * gamma / (gamma + 1.0)) * (mn1 * mn1 - 1.0);
	}

	public static double obliqueShockTempRatio(double mach, double shockAngle, double gamma) {
		validateGamma(gamma);
		double mn1 = mach * Math.sin(shockAngle);
		double pressureRatio = 1.0 + (2.0 * gamma / (gamma + 1.0)) * (mn1 * mn1 - 1.0);
		double densityRatio = ((gamma + 1.0) * mn1 * mn1) / ((gamma - 1.0) * mn1 * mn1 + 2.0);
		return pressureRatio / densityRatio;
	}

	public static double obliqueShockMach2(double mach, double shockAngle, double deflectionAngle, double gamma) {
		validateGamma(gamma);
		double mn1 = mach * Math.sin(shockAngle);
		double mn2Squared = (1.0 + 0.5 * (gamma - 1.0) * mn1 * mn1)
				/ (gamma * mn1 * mn1 - 0.5 * (gamma - 1.0));
		double denominator = Math.sin(Math.max(1.0e-9, shockAngle - Math.abs(deflectionAngle)));
		return Math.sqrt(Math.max(0.0, mn2Squared)) / denominator;
	}

	public static double maxDeflectionAngle(double mach, double gamma) {
		validateGamma(gamma);
		if (mach <= 1.0) {
			return 0.0;
		}
		double betaMin = Math.asin(1.0 / mach) + 1.0e-5;
		double betaMax = 0.5 * Math.PI - 1.0e-5;
		double maxTheta = 0.0;
		for (int i = 0; i <= 2000; i++) {
			double beta = betaMin + (betaMax - betaMin) * i / 2000.0;
			double theta = thetaFromBeta(mach, beta, gamma);
			if (Double.isFinite(theta)) {
				maxTheta = Math.max(maxTheta, theta);
			}
		}
		return maxTheta;
	}

	public static double prandtlGlauertCp(double cpIncomp, double mach) {
		if (mach >= 1.0) {
			throw new IllegalArgumentException("Prandtl-Glauert correction is only valid for M < 1");
		}
		return cpIncomp / Math.sqrt(Math.max(1.0e-9, 1.0 - mach * mach));
	}

	public static double karmanTsienCp(double cpIncomp, double mach) {
		if (mach >= 1.0) {
			throw new IllegalArgumentException("Karman-Tsien correction is only valid for M < 1");
		}
		double beta = Math.sqrt(Math.max(1.0e-9, 1.0 - mach * mach));
		double correction = mach * mach * cpIncomp / (2.0 * (1.0 + beta));
		return cpIncomp / (beta + correction);
	}

	public static double sutherlandViscosity(double temperatureK) {
		if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
			throw new IllegalArgumentException("Temperature must be positive");
		}
		double ratio = temperatureK / SUTHERLAND_REFERENCE_TEMPERATURE_K;
		return SUTHERLAND_REFERENCE_VISCOSITY_PA_S * Math.pow(ratio, 1.5)
				* (SUTHERLAND_REFERENCE_TEMPERATURE_K + SUTHERLAND_CONSTANT_K)
				/ (temperatureK + SUTHERLAND_CONSTANT_K);
	}

	public static double speedOfSound(double temperatureK, double gamma, double gasConstant) {
		validateGamma(gamma);
		if (!Double.isFinite(temperatureK) || temperatureK <= 0.0 || !Double.isFinite(gasConstant) || gasConstant <= 0.0) {
			throw new IllegalArgumentException("Temperature and gas constant must be positive");
		}
		return Math.sqrt(gamma * gasConstant * temperatureK);
	}

	private static double prandtlMeyerDerivative(double mach, double gamma) {
		double h = Math.max(1.0e-6, mach * 1.0e-6);
		double left = Math.max(1.0, mach - h);
		double right = mach + h;
		return (prandtlMeyerAngle(right, gamma) - prandtlMeyerAngle(left, gamma)) / (right - left);
	}

	private static double thetaBetaMResidual(double mach, double theta, double beta, double gamma) {
		return thetaFromBeta(mach, beta, gamma) - theta;
	}

	private static double thetaFromBeta(double mach, double beta, double gamma) {
		double sinBeta = Math.sin(beta);
		double machSinSquared = mach * mach * sinBeta * sinBeta;
		if (machSinSquared <= 1.0) {
			return 0.0;
		}
		double numerator = 2.0 * (machSinSquared - 1.0);
		double denominator = Math.tan(beta) * (mach * mach * (gamma + Math.cos(2.0 * beta)) + 2.0);
		return Math.atan(numerator / denominator);
	}

	private static void validateGamma(double gamma) {
		if (!Double.isFinite(gamma) || gamma <= MIN_GAMMA) {
			throw new IllegalArgumentException("Gamma must be finite and greater than one");
		}
	}
}

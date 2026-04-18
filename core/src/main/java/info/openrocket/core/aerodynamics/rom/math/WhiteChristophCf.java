package info.openrocket.core.aerodynamics.rom.math;

/**
 * Turbulent skin-friction closures used by the Phase 1 ROM scaffold.
 *
 * <p>Primary references: White, Viscous Fluid Flow, Eq. 7-92; Ludwieg &
 * Tillmann; Karman-Schoenherr.
 */
public final class WhiteChristophCf {
	private static final double MIN_REYNOLDS = 1.0;
	private static final double AVERAGE_FLAT_PLATE_A = 0.074; // Schlichting average turbulent plate correlation.
	private static final double AVERAGE_FLAT_PLATE_B = 1742.0; // Schlichting offset term for finite-length plate.
	private static final double AVERAGE_FLAT_PLATE_EXPONENT = 0.2;
	private static final double LUDWIEG_TILLMANN_COEFFICIENT = 0.246;
	private static final double LUDWIEG_TILLMANN_SHAPE_EXPONENT = -0.678;
	private static final double LUDWIEG_TILLMANN_RE_EXPONENT = -0.268;
	private static final double KARMAN_SCHOENHERR_A = 4.15;
	private static final double KARMAN_SCHOENHERR_B = 1.7;
	private static final int KARMAN_SCHOENHERR_NEWTON_STEPS = 3;

	private WhiteChristophCf() {
	}

	public static double whiteChristoph(double reynoldsX, double edgeTemperature, double wallTemperature,
			double adiabaticWallTemperature, double edgeMach, double gamma) {
		double boundedRe = Math.max(MIN_REYNOLDS, reynoldsX);
		double incompressible = averageFlatPlateCf(boundedRe);
		double referenceTemperature = EckertReference.referenceTemperature(edgeTemperature, wallTemperature,
				adiabaticWallTemperature);
		double muEdge = GasDynamics.sutherlandViscosity(edgeTemperature);
		double muReference = GasDynamics.sutherlandViscosity(referenceTemperature);
		double temperatureFactor = Math.sqrt(edgeTemperature / Math.max(1.0e-9, referenceTemperature));
		double viscosityFactor = muEdge / Math.max(1.0e-12, muReference);
		double vanDriestLikeFactor = 1.0 / (1.0 + 0.08 * gamma * edgeMach * edgeMach);
		return Math.max(1.0e-6, incompressible * temperatureFactor * viscosityFactor * vanDriestLikeFactor);
	}

	public static double ludwiegTillmann(double reynoldsTheta, double shapeFactor) {
		double boundedReTheta = Math.max(100.0, reynoldsTheta);
		return LUDWIEG_TILLMANN_COEFFICIENT
				* Math.pow(10.0, LUDWIEG_TILLMANN_SHAPE_EXPONENT * shapeFactor)
				* Math.pow(boundedReTheta, LUDWIEG_TILLMANN_RE_EXPONENT);
	}

	public static double karmanSchoenherr(double reynoldsTheta) {
		double cf = 0.003;
		double reTheta = Math.max(100.0, reynoldsTheta);
		for (int i = 0; i < KARMAN_SCHOENHERR_NEWTON_STEPS; i++) {
			double sqrtCf = Math.sqrt(Math.max(1.0e-9, cf));
			double logArgument = Math.max(10.0, reTheta * cf);
			double residual = 1.0 / sqrtCf - KARMAN_SCHOENHERR_A * Math.log10(logArgument) - KARMAN_SCHOENHERR_B;
			double derivative = -0.5 / (cf * sqrtCf) - KARMAN_SCHOENHERR_A / (Math.log(10.0) * cf);
			cf = Math.max(1.0e-6, cf - residual / Math.min(-1.0e-6, derivative));
		}
		return cf;
	}

	public static double eckertCorrectedCf(double reynoldsX, double edgeTemperature, double wallTemperature,
			double adiabaticWallTemperature, double edgeMach, double gamma) {
		double referenceTemperature = EckertReference.referenceTemperature(edgeTemperature, wallTemperature,
				adiabaticWallTemperature);
		double referenceRe = reynoldsX * edgeTemperature / Math.max(1.0e-9, referenceTemperature);
		double incompressible = averageFlatPlateCf(Math.max(MIN_REYNOLDS, referenceRe));
		double viscosityRatio = GasDynamics.sutherlandViscosity(edgeTemperature)
				/ Math.max(1.0e-12, GasDynamics.sutherlandViscosity(referenceTemperature));
		double machDamping = 1.0 / (1.0 + 0.06 * gamma * edgeMach * edgeMach);
		return Math.max(1.0e-6, incompressible * viscosityRatio * machDamping);
	}

	private static double averageFlatPlateCf(double reynoldsX) {
		double base = AVERAGE_FLAT_PLATE_A / Math.pow(reynoldsX, AVERAGE_FLAT_PLATE_EXPONENT)
				- AVERAGE_FLAT_PLATE_B / reynoldsX;
		return Math.max(1.0e-6, base);
	}
}

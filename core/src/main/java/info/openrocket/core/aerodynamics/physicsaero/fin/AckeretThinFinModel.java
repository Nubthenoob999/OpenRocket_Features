package info.openrocket.core.aerodynamics.physicsaero.fin;

/** Linearized supersonic pressure model, deliberately guarded away from M_n=1. */
public final class AckeretThinFinModel {
	public static final String METHOD_ID = "ACKERET_THIN_FIN_V1";
	private final double sonicMargin;
	private final double maximumThicknessRatio;
	private final double maximumAngleRad;

	public AckeretThinFinModel() { this(0.05, 0.04, Math.toRadians(8)); }
	public AckeretThinFinModel(double sonicMargin, double maximumThicknessRatio, double maximumAngleRad) {
		if (sonicMargin <= 0 || maximumThicknessRatio <= 0 || maximumAngleRad <= 0)
			throw new IllegalArgumentException("invalid Ackeret limits");
		this.sonicMargin = sonicMargin; this.maximumThicknessRatio = maximumThicknessRatio; this.maximumAngleRad = maximumAngleRad;
	}
	public boolean isValid(double normalMach, double thicknessRatio, double incidenceRad, double maximumSurfaceTurnRad) {
		return normalMach > 1 + sonicMargin && thicknessRatio >= 0 && thicknessRatio <= maximumThicknessRatio
				&& Math.abs(incidenceRad) <= maximumAngleRad && Math.abs(maximumSurfaceTurnRad) <= maximumAngleRad;
	}
	public PressureResult evaluate(double normalMach, double incidenceRad, double areaM2, double dynamicPressurePa) {
		if (!isValid(normalMach, 0, incidenceRad, incidenceRad) || areaM2 <= 0 || dynamicPressurePa < 0)
			throw new IllegalArgumentException("Ackeret method outside validity domain");
		double beta = Math.sqrt(normalMach * normalMach - 1);
		double cpLower = 2 * incidenceRad / beta;
		double cpUpper = -cpLower;
		return new PressureResult(cpUpper, cpLower, dynamicPressurePa * (cpLower - cpUpper) * areaM2,
				4 / beta, METHOD_ID);
	}
	public record PressureResult(double upperCp, double lowerCp, double normalForceN,
			double liftSlopePerRad, String methodId) {}
}

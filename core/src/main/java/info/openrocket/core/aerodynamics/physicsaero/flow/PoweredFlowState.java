package info.openrocket.core.aerodynamics.physicsaero.flow;

/** Propulsive boundary condition at the nozzle exit plane. */
public record PoweredFlowState(double poweredFraction, double thrustN, double nozzleExitMach,
		double nozzleExitAreaM2, double nozzleExitPressurePa, double exhaustGamma,
		double ambientPressurePa, Resolution resolution) {
	public enum Resolution {
		NONE,
		RESOLVED_THERMODYNAMIC_STATE,
		NOZZLE_GEOMETRY_ONLY
	}

	public PoweredFlowState(double poweredFraction, double thrustN, double nozzleExitMach,
			double nozzleExitAreaM2, double nozzleExitPressurePa, double exhaustGamma,
			double ambientPressurePa) {
		this(poweredFraction, thrustN, nozzleExitMach, nozzleExitAreaM2,
				nozzleExitPressurePa, exhaustGamma, ambientPressurePa,
				poweredFraction > 0 && thrustN > 0 && nozzleExitMach > 0
						&& nozzleExitAreaM2 > 0 && nozzleExitPressurePa > 0
						? Resolution.RESOLVED_THERMODYNAMIC_STATE : Resolution.NONE);
	}

	public PoweredFlowState {
		if (resolution == null) {
			throw new IllegalArgumentException("MISSING_POWERED_FLOW_RESOLUTION");
		}
		if (!Double.isFinite(poweredFraction) || poweredFraction < 0 || poweredFraction > 1
				|| !Double.isFinite(thrustN) || thrustN < 0
				|| !Double.isFinite(nozzleExitMach) || nozzleExitMach < 0
				|| !Double.isFinite(nozzleExitAreaM2) || nozzleExitAreaM2 < 0
				|| !Double.isFinite(nozzleExitPressurePa) || nozzleExitPressurePa < 0
				|| !Double.isFinite(exhaustGamma) || exhaustGamma <= 1
				|| !Double.isFinite(ambientPressurePa) || ambientPressurePa <= 0) {
			throw new IllegalArgumentException("INVALID_POWERED_FLOW_STATE");
		}
		boolean anyResolvedParameter = thrustN > 0 || nozzleExitMach > 0
				|| nozzleExitAreaM2 > 0 || nozzleExitPressurePa > 0;
		if (resolution == Resolution.RESOLVED_THERMODYNAMIC_STATE) {
			if (poweredFraction <= 0 || thrustN <= 0 || nozzleExitMach <= 0
					|| nozzleExitAreaM2 <= 0 || nozzleExitPressurePa <= 0) {
				throw new IllegalArgumentException("MISSING_POWERED_FLOW_PARAMETERS");
			}
		} else if (resolution == Resolution.NOZZLE_GEOMETRY_ONLY) {
			if (poweredFraction <= 0 || nozzleExitAreaM2 <= 0
					|| thrustN != 0 || nozzleExitMach != 0 || nozzleExitPressurePa != 0) {
				throw new IllegalArgumentException("INVALID_NOZZLE_GEOMETRY_ONLY_STATE");
			}
		} else if (anyResolvedParameter) {
			throw new IllegalArgumentException("UNDECLARED_POWERED_FLOW_PARAMETERS");
		}
	}

	public static PoweredFlowState coast(double ambientPressurePa) {
		return new PoweredFlowState(0, 0, 0, 0, 0, 1.25, ambientPressurePa,
				Resolution.NONE);
	}

	/**
	 * Explicit boost-axis state for cases where motor/nozzle thermodynamics are not
	 * available.  The table retains coast aerodynamics and records that the
	 * powered increment is unmodeled instead of silently falling back wholesale.
	 */
	public static PoweredFlowState unmodeledPoweredBaseline(double ambientPressurePa) {
		return new PoweredFlowState(1, 0, 0, 0, 0, 1.25, ambientPressurePa,
				Resolution.NONE);
	}

	/**
	 * Powered state for RASAero-compatible inputs that provide nozzle diameter
	 * but no chamber, throat, or exit-plane thermodynamic measurements.
	 */
	public static PoweredFlowState nozzleGeometryOnly(double poweredFraction,
			double nozzleExitAreaM2, double ambientPressurePa) {
		return new PoweredFlowState(poweredFraction, 0, 0, nozzleExitAreaM2, 0,
				1.25, ambientPressurePa, Resolution.NOZZLE_GEOMETRY_ONLY);
	}

	public boolean powered() {
		return poweredFraction > 0;
	}

	public boolean hasResolvedNozzleState() {
		return powered() && resolution == Resolution.RESOLVED_THERMODYNAMIC_STATE;
	}

	public boolean hasPoweredClosureState() {
		return hasResolvedNozzleState()
				|| powered() && resolution == Resolution.NOZZLE_GEOMETRY_ONLY;
	}
}

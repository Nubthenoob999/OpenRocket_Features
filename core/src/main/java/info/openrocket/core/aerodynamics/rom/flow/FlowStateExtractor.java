package info.openrocket.core.aerodynamics.rom.flow;

import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;

public class FlowStateExtractor {
	public FlowState extract(FlightConditions conditions, double referenceLength, double plumeState) {
		AtmosphericConditions atmosphere = conditions.getAtmosphericConditions();
		double mach = sanitizeNonNegative(conditions.getMach());
		double speedOfSound = sanitizePositive(atmosphere.getMachSpeed(), 340.0);
		double velocity = sanitizeNonNegative(conditions.getVelocity());
		if (velocity <= 0.0) {
			velocity = mach * speedOfSound;
		}
		double density = sanitizePositive(atmosphere.getDensity(), 1.225);
		double staticPressure = sanitizePositive(atmosphere.getPressure(), 101325.0);
		double staticTemperature = sanitizePositive(atmosphere.getTemperature(), 288.15);
		double viscosity = sanitizePositive(atmosphere.getKinematicViscosity(), 1.5e-5);
		double reynolds = velocity * Math.max(1e-6, referenceLength) / viscosity;
		double dynamicPressure = 0.5 * density * velocity * velocity;
		double aoa = sanitizeFinite(conditions.getAOA(), 0.0);
		double theta = sanitizeFinite(conditions.getTheta(), 0.0);
		double sideslip = aoa * Math.sin(theta);
		return new FlowState(mach, reynolds, dynamicPressure, staticPressure, staticTemperature,
				density, speedOfSound, viscosity, velocity, aoa, Math.toDegrees(aoa),
				sideslip, Math.toDegrees(sideslip), plumeState > 1e-6, clamp01(plumeState), referenceLength);
	}

	private static double sanitizeFinite(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}

	private static double sanitizeNonNegative(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double sanitizePositive(double value, double fallback) {
		return (Double.isFinite(value) && value > 0.0) ? value : fallback;
	}

	private static double clamp01(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}

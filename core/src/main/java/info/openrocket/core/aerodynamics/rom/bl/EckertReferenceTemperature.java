package info.openrocket.core.aerodynamics.rom.bl;

import info.openrocket.core.aerodynamics.rom.math.EckertReference;
import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

public final class EckertReferenceTemperature {
	private static final double AIR_GAMMA = 1.4;

	private EckertReferenceTemperature() {
	}

	public static double referenceTemperature(double staticTemperature, double mach) {
		if (!Double.isFinite(staticTemperature) || !Double.isFinite(mach) || staticTemperature <= 0.0) {
			return Double.NaN;
		}
		double boundedMach = Math.max(0.0, mach);
		double wallTemperature = staticTemperature;
		double adiabaticWallTemperature = EckertReference.adiabaticWallTemp(staticTemperature, boundedMach, AIR_GAMMA, true);
		return EckertReference.referenceTemperature(staticTemperature, wallTemperature, adiabaticWallTemperature);
	}

	public static double viscosityRatio(double staticTemperature, double referenceTemperature) {
		if (!Double.isFinite(staticTemperature) || !Double.isFinite(referenceTemperature)
				|| staticTemperature <= 0.0 || referenceTemperature <= 0.0) {
			return 1.0;
		}
		return GasDynamics.sutherlandViscosity(referenceTemperature)
				/ Math.max(1.0e-12, GasDynamics.sutherlandViscosity(staticTemperature));
	}
}

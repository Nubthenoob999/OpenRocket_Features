package info.openrocket.core.aerodynamics.rom.outer;

import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

public class TaylorMaccollTable {
	public double estimateCpMax(double mach) {
		if (!Double.isFinite(mach) || mach <= 0.0) {
			return 0.0;
		}
		return GasDynamics.cpMax(mach, 1.4);
	}
}

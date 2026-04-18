package info.openrocket.core.aerodynamics.rom.bl;

import info.openrocket.core.aerodynamics.rom.math.ENTransition;

public final class MichelTransition {

	private MichelTransition() {
	}

	public static boolean shouldTransition(double reynoldsX, double reynoldsTheta) {
		if (!Double.isFinite(reynoldsX) || !Double.isFinite(reynoldsTheta)
				|| reynoldsX <= 0.0 || reynoldsTheta <= 0.0) {
			return false;
		}
		return reynoldsTheta >= ENTransition.michelTransitionReTheta(reynoldsX);
	}
}

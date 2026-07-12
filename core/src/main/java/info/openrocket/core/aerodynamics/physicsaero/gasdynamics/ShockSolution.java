package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;

public record ShockSolution(GasState upstream, GasState downstream, TotalState upstreamTotal,
		TotalState downstreamTotal, double shockAngleRad, double turnAngleRad, Branch branch,
		Attachment attachment, double entropyRiseJkgK, double residual) {
	public enum Branch { NORMAL, WEAK, STRONG }
	public enum Attachment { ATTACHED, NEAR_DETACHMENT, DETACHED }
}

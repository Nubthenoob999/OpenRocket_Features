package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;

public record ExpansionSolution(GasState upstream, GasState downstream, TotalState conservedTotalState,
		double turnAngleRad, double residual) {}

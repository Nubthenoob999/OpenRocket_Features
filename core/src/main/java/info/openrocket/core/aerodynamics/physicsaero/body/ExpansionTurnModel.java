package info.openrocket.core.aerodynamics.physicsaero.body;

import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;

public final class ExpansionTurnModel {
	public static final String METHOD_ID = "PRANDTL_MEYER_EXPANSION_V1";
	public ExpansionEvent apply(SurfaceState upstream, double xM, double radiusM, double expansionTurnRad,
			ThermodynamicModel model) {
		ExpansionSolution solution = PrandtlMeyerCalculator.expand(upstream.staticState(), expansionTurnRad, model);
		double q = 0.5 * upstream.staticState().densityKgM3() * upstream.staticState().velocityMS() * upstream.staticState().velocityMS();
		double cp = (solution.downstream().pressurePa() - upstream.staticState().pressurePa()) / q;
		SurfaceState downstream = SurfaceState.of(xM, radiusM, solution.downstream(), upstream.totalState(),
				upstream.flowAngleRad() - expansionTurnRad, cp, model, METHOD_ID);
		return new ExpansionEvent(xM, expansionTurnRad, solution.downstream().mach(), 1, upstream, downstream, METHOD_ID);
	}
}

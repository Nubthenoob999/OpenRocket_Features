package info.openrocket.core.aerodynamics.physicsaero.body;

import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;

public final class DiscreteCornerShockModel {
	public static final String METHOD_ID = "OBLIQUE_SHOCK_CORNER_FALLBACK_V1";
	public ShockEvent apply(SurfaceState upstream, double xM, double radiusM, double compressionTurnRad,
			ThermodynamicModel model) {
		ShockSolution shock = ObliqueShockCalculator.solve(upstream.staticState(), compressionTurnRad, ShockSolution.Branch.WEAK, model);
		if (shock.attachment() == ShockSolution.Attachment.DETACHED) throw new GasDynamicsException("DETACHED_COMPRESSION_CORNER");
		double mn = upstream.staticState().mach() * Math.sin(shock.shockAngleRad());
		GasState normalUp = new GasState(mn, upstream.staticState().pressurePa(), upstream.staticState().temperatureK(),
				upstream.staticState().densityKgM3(), mn * model.speedOfSound(upstream.staticState().temperatureK()));
		ShockSolution normal = NormalShockCalculator.solve(normalUp, model);
		double ptRatio = normal.downstreamTotal().pressurePa() / normal.upstreamTotal().pressurePa();
		TotalState total = new TotalState(upstream.totalState().pressurePa() * ptRatio, upstream.totalState().temperatureK(),
				upstream.totalState().densityKgM3() * ptRatio);
		GasState downstreamGas = shock.downstream();
		double q = 0.5 * upstream.staticState().densityKgM3() * upstream.staticState().velocityMS() * upstream.staticState().velocityMS();
		double cp = (downstreamGas.pressurePa() - upstream.staticState().pressurePa()) / q;
		SurfaceState downstream = SurfaceState.of(xM, radiusM, downstreamGas, total,
				upstream.flowAngleRad() + compressionTurnRad, cp, model, METHOD_ID);
		return new ShockEvent(xM, compressionTurnRad, shock.shockAngleRad(), shock.attachment(), ptRatio,
				upstream, downstream, METHOD_ID);
	}
}

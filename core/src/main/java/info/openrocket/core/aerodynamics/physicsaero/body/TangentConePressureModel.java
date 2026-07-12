package info.openrocket.core.aerodynamics.physicsaero.body;

import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;

public final class TangentConePressureModel {
	public static final String METHOD_ID = "TANGENT_CONE_LOCAL_SLOPE_V1";
	public SurfaceState evaluate(SurfaceState upstream, double xM, double radiusM, double tangentAngleRad,
			ThermodynamicModel model) {
		TaylorMaccollSolution cone = new TaylorMaccollSolver().solve(upstream.staticState(), Math.abs(tangentAngleRad), model);
		if (!cone.attached()) throw new GasDynamicsException("DETACHED_TANGENT_CONE");
		// A local tangent-cone construction is a pressure approximation, not a series of physical shocks.
		// Preserve the segment's incoming total state so station refinement cannot accumulate entropy.
		double pressure = upstream.staticState().pressurePa() + cone.wallPressureCoefficient()
				* 0.5 * upstream.staticState().densityKgM3() * upstream.staticState().velocityMS() * upstream.staticState().velocityMS();
		double gamma = model.gamma(upstream.staticState().temperatureK());
		double ratio = Math.max(1, upstream.totalState().pressurePa() / pressure);
		double mach2 = 2 / (gamma - 1) * (Math.pow(ratio, (gamma - 1) / gamma) - 1);
		GasState local = IsentropicRelations.staticState(upstream.totalState(), Math.sqrt(Math.max(0, mach2)), model);
		return SurfaceState.of(xM, radiusM, local, upstream.totalState(), tangentAngleRad,
				cone.wallPressureCoefficient(), model, METHOD_ID);
	}
}

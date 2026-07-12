package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.aerodynamics.physicsaero.math.BrentRootSolver;

public final class PrandtlMeyerCalculator {
	private PrandtlMeyerCalculator() {}
	public static double angle(double mach, double gamma) {
		if (mach <= 1 || gamma <= 1) throw new GasDynamicsException("Prandtl-Meyer relation requires Mach > 1");
		double root = Math.sqrt(mach * mach - 1);
		return Math.sqrt((gamma + 1) / (gamma - 1))
				* Math.atan(Math.sqrt((gamma - 1) / (gamma + 1)) * root) - Math.atan(root);
	}
	public static double inverse(double nu, double gamma) {
		if (nu < 0) throw new GasDynamicsException("expansion angle must be nonnegative");
		if (nu == 0) return 1;
		double max = Math.PI / 2 * (Math.sqrt((gamma + 1) / (gamma - 1)) - 1);
		if (nu >= max) throw new GasDynamicsException("Prandtl-Meyer angle exceeds finite-Mach limit");
		double hi = 2;
		while (angle(hi, gamma) < nu) hi *= 2;
		return new BrentRootSolver().solve(m -> angle(m, gamma) - nu, 1 + 1e-12, hi, 1e-10, 100);
	}
	public static ExpansionSolution expand(GasState upstream, double turnAngleRad, ThermodynamicModel model) {
		if (upstream.mach() <= 1 || turnAngleRad < 0) throw new GasDynamicsException("invalid expansion input");
		double gamma = model.gamma(upstream.temperatureK());
		double target = angle(upstream.mach(), gamma) + turnAngleRad;
		double m2 = inverse(target, gamma);
		TotalState total = IsentropicRelations.totalState(upstream, model);
		GasState down = IsentropicRelations.staticState(total, m2, model);
		return new ExpansionSolution(upstream, down, total, turnAngleRad, Math.abs(angle(m2, gamma) - target));
	}
}

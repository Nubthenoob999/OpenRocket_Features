package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.aerodynamics.physicsaero.math.BrentRootSolver;

public final class ObliqueShockCalculator {
	private static final double GUARD = 1e-8;
	private static final double NEAR_DETACHMENT = 1e-5;
	private ObliqueShockCalculator() {}

	public static ShockSolution solve(GasState upstream, double turnAngleRad,
			ShockSolution.Branch branch, ThermodynamicModel model) {
		if (upstream.mach() <= 1 || turnAngleRad < 0 || branch == ShockSolution.Branch.NORMAL) {
			throw new GasDynamicsException("invalid oblique-shock input");
		}
		double m = upstream.mach(), g = model.gamma(upstream.temperatureK());
		double mu = Math.asin(1 / m);
		double betaAtMax = maximizeTurning(m, g, mu + GUARD, Math.PI / 2 - GUARD);
		double maxTurn = turningAngle(m, g, betaAtMax);
		if (turnAngleRad > maxTurn + 1e-12) {
			return new ShockSolution(upstream, upstream, IsentropicRelations.totalState(upstream, model),
					IsentropicRelations.totalState(upstream, model), Double.NaN, turnAngleRad, branch,
					ShockSolution.Attachment.DETACHED, 0, turnAngleRad - maxTurn);
		}
		if (turnAngleRad == 0) {
			return new ShockSolution(upstream, upstream, IsentropicRelations.totalState(upstream, model),
					IsentropicRelations.totalState(upstream, model), branch == ShockSolution.Branch.WEAK ? mu : Math.PI / 2,
					0, branch, ShockSolution.Attachment.ATTACHED, 0, 0);
		}
		double lo = branch == ShockSolution.Branch.WEAK ? mu + GUARD : betaAtMax;
		double hi = branch == ShockSolution.Branch.WEAK ? betaAtMax : Math.PI / 2 - GUARD;
		double beta = new BrentRootSolver().solve(b -> turningAngle(m, g, b) - turnAngleRad, lo, hi, 1e-10, 100);
		double mn1 = m * Math.sin(beta);
		GasState normalUp = new GasState(mn1, upstream.pressurePa(), upstream.temperatureK(), upstream.densityKgM3(),
				mn1 * model.speedOfSound(upstream.temperatureK()));
		ShockSolution normal = NormalShockCalculator.solve(normalUp, model);
		double m2 = normal.downstream().mach() / Math.sin(beta - turnAngleRad);
		GasState down = new GasState(m2, normal.downstream().pressurePa(), normal.downstream().temperatureK(),
				normal.downstream().densityKgM3(), m2 * model.speedOfSound(normal.downstream().temperatureK()));
		TotalState total1 = IsentropicRelations.totalState(upstream, model);
		TotalState total2 = new TotalState(normal.downstreamTotal().pressurePa(), total1.temperatureK(),
				normal.downstreamTotal().pressurePa() / (model.gasConstant() * total1.temperatureK()));
		double residual = Math.abs(turningAngle(m, g, beta) - turnAngleRad);
		ShockSolution.Attachment attachment = maxTurn - turnAngleRad <= NEAR_DETACHMENT
				? ShockSolution.Attachment.NEAR_DETACHMENT : ShockSolution.Attachment.ATTACHED;
		return new ShockSolution(upstream, down, total1, total2, beta, turnAngleRad, branch, attachment,
				normal.entropyRiseJkgK(), residual);
	}

	public static double maximumTurningAngle(double mach, double gamma) {
		if (mach <= 1) throw new GasDynamicsException("supersonic Mach required");
		double mu = Math.asin(1 / mach);
		return turningAngle(mach, gamma, maximizeTurning(mach, gamma, mu + GUARD, Math.PI / 2 - GUARD));
	}

	static double turningAngle(double mach, double gamma, double beta) {
		double sin = Math.sin(beta);
		double numerator = 2 / Math.tan(beta) * (mach * mach * sin * sin - 1);
		double denominator = mach * mach * (gamma + Math.cos(2 * beta)) + 2;
		return Math.atan(numerator / denominator);
	}

	private static double maximizeTurning(double mach, double gamma, double lower, double upper) {
		double a = lower, b = upper;
		for (int i = 0; i < 100; i++) {
			double m1 = a + (b - a) / 3, m2 = b - (b - a) / 3;
			if (turningAngle(mach, gamma, m1) < turningAngle(mach, gamma, m2)) a = m1; else b = m2;
		}
		return (a + b) / 2;
	}
}

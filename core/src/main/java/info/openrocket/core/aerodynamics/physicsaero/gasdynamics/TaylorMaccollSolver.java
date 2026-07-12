package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.aerodynamics.physicsaero.math.AdaptiveOdeSolver;
import info.openrocket.core.aerodynamics.physicsaero.math.BrentRootSolver;

/** Exact sharp-cone Taylor-Maccoll solver using maximum-adiabatic-speed normalization. */
public final class TaylorMaccollSolver {
	private static final double ANGLE_GUARD = 1e-7;
	private static final double ABS_TOLERANCE = 1e-10;
	private static final double REL_TOLERANCE = 1e-9;
	private static final int SCAN_INTERVALS = 240;
	public TaylorMaccollSolution solve(GasState freestream, double coneHalfAngleRad, ThermodynamicModel model) {
		if (freestream.mach() <= 1 || coneHalfAngleRad <= 0 || coneHalfAngleRad >= Math.PI / 2)
			throw new GasDynamicsException("Taylor-Maccoll requires a sharp supersonic cone");
		double gamma = model.gamma(freestream.temperatureK());
		double lower = Math.max(Math.asin(1 / freestream.mach()), coneHalfAngleRad) + ANGLE_GUARD;
		double upper = Math.PI / 2 - ANGLE_GUARD;
		List<Bracket> brackets = scan(freestream, coneHalfAngleRad, gamma, lower, upper);
		if (brackets.isEmpty()) return TaylorMaccollSolution.detached(freestream.mach(), coneHalfAngleRad);
		Bracket weak = brackets.get(0);
		double beta = new BrentRootSolver().solve(b -> trajectory(freestream, coneHalfAngleRad, gamma, b).residual,
				weak.lower, weak.upper, 1e-10, 100);
		Trajectory trajectory = trajectory(freestream, coneHalfAngleRad, gamma, beta);
		double normalizedSpeed2 = trajectory.wallVr * trajectory.wallVr + trajectory.wallVtheta * trajectory.wallVtheta;
		double wallMach2 = 2 * normalizedSpeed2 / ((gamma - 1) * (1 - normalizedSpeed2));
		if (!(wallMach2 > 0) || !Double.isFinite(wallMach2)) throw new GasDynamicsException("nonphysical Taylor-Maccoll wall state");
		double mn1 = freestream.mach() * Math.sin(beta);
		GasState normalUp = new GasState(mn1, freestream.pressurePa(), freestream.temperatureK(), freestream.densityKgM3(),
				mn1 * model.speedOfSound(freestream.temperatureK()));
		ShockSolution normal = NormalShockCalculator.solve(normalUp, model);
		double pressureRatio = normal.downstream().pressurePa() / normal.upstream().pressurePa();
		double densityRatio = normal.downstream().densityKgM3() / normal.upstream().densityKgM3();
		double vNormal = freestream.velocityMS() * Math.sin(beta) / densityRatio;
		double vTangential = freestream.velocityMS() * Math.cos(beta);
		double postSpeed = Math.hypot(vNormal, vTangential);
		double postT = normal.downstream().temperatureK();
		double postMach = postSpeed / model.speedOfSound(postT);
		GasState postShock = new GasState(postMach, freestream.pressurePa() * pressureRatio, postT,
				freestream.densityKgM3() * densityRatio, postSpeed);
		TotalState upstreamTotal = IsentropicRelations.totalState(freestream, model);
		double totalPressureRatio = normal.downstreamTotal().pressurePa() / normal.upstreamTotal().pressurePa();
		TotalState downstreamTotal = new TotalState(upstreamTotal.pressurePa() * totalPressureRatio,
				upstreamTotal.temperatureK(), upstreamTotal.pressurePa() * totalPressureRatio / (model.gasConstant() * upstreamTotal.temperatureK()));
		GasState wall = IsentropicRelations.staticState(downstreamTotal, Math.sqrt(wallMach2), model);
		double dynamicPressure = 0.5 * freestream.densityKgM3() * freestream.velocityMS() * freestream.velocityMS();
		double cp = (wall.pressurePa() - freestream.pressurePa()) / dynamicPressure;
		ShockSolution.Attachment attachment = beta > upper - Math.toRadians(0.05)
				? ShockSolution.Attachment.NEAR_DETACHMENT : ShockSolution.Attachment.ATTACHED;
		return new TaylorMaccollSolution(freestream.mach(), coneHalfAngleRad, beta, postShock, wall,
				downstreamTotal, cp, totalPressureRatio, attachment, Math.abs(trajectory.residual),
				trajectory.steps, TaylorMaccollSolution.METHOD_ID);
	}
	private static List<Bracket> scan(GasState state, double cone, double gamma, double lower, double upper) {
		List<Bracket> brackets = new ArrayList<>(); double lastBeta = Double.NaN, lastResidual = Double.NaN;
		for (int i = 0; i <= SCAN_INTERVALS; i++) {
			double beta = lower + (upper - lower) * i / SCAN_INTERVALS;
			try {
				double residual = trajectory(state, cone, gamma, beta).residual;
				if (Double.isFinite(lastResidual) && residual * lastResidual <= 0) brackets.add(new Bracket(lastBeta, beta));
				lastBeta = beta; lastResidual = residual;
			} catch (RuntimeException ignored) { lastBeta = Double.NaN; lastResidual = Double.NaN; }
		}
		return brackets;
	}
	private static Trajectory trajectory(GasState freestream, double cone, double gamma, double beta) {
		double mach = freestream.mach(), mn1 = mach * Math.sin(beta);
		if (mn1 <= 1) throw new GasDynamicsException("candidate shock is below Mach angle");
		double densityRatio = (gamma + 1) * mn1 * mn1 / ((gamma - 1) * mn1 * mn1 + 2);
		double normalizedFreestream = mach / Math.sqrt(mach * mach + 2 / (gamma - 1));
		double vr = normalizedFreestream * Math.cos(beta);
		double vtheta = -normalizedFreestream * Math.sin(beta) / densityRatio;
		AdaptiveOdeSolver.Result result = new AdaptiveOdeSolver().integrate((theta, y, dydt) -> {
			double radial = y[0], polar = y[1], remaining = 1 - radial * radial - polar * polar;
			double numerator = polar * polar * radial - 0.5 * (gamma - 1) * remaining
					* (2 * radial + polar / Math.tan(theta));
			double denominator = 0.5 * (gamma - 1) * remaining - polar * polar;
			if (Math.abs(denominator) < 1e-12 || remaining <= 0) throw new GasDynamicsException("singular Taylor-Maccoll trajectory");
			dydt[0] = polar; dydt[1] = numerator / denominator;
		}, beta, cone, new double[] {vr, vtheta}, ABS_TOLERANCE, REL_TOLERANCE, 20000);
		double[] wall = result.y();
		return new Trajectory(wall[1], wall[0], wall[1], result.acceptedSteps());
	}
	private record Bracket(double lower, double upper) {}
	private record Trajectory(double residual, double wallVr, double wallVtheta, int steps) {}
}

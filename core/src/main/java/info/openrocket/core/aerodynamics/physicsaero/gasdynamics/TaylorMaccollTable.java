package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;

/** Deterministically generated Taylor-Maccoll reference table; no extrapolation. */
public final class TaylorMaccollTable {
	private final double[] machAxis;
	private final double[] coneAngleAxis;
	private final double gamma;
	private final List<TaylorMaccollSolution> values;
	private TaylorMaccollTable(double[] machAxis, double[] coneAngleAxis, double gamma, List<TaylorMaccollSolution> values) {
		this.machAxis = machAxis.clone(); this.coneAngleAxis = coneAngleAxis.clone(); this.gamma = gamma; this.values = List.copyOf(values);
	}
	public static TaylorMaccollTable generate(double[] machAxis, double[] coneAngleAxis, GasState reference,
			PerfectGasAir model) {
		List<TaylorMaccollSolution> values = new ArrayList<>(); TaylorMaccollSolver solver = new TaylorMaccollSolver();
		for (double mach : machAxis) for (double angle : coneAngleAxis) {
			GasState state = new GasState(mach, reference.pressurePa(), reference.temperatureK(), reference.densityKgM3(),
					mach * model.speedOfSound(reference.temperatureK()));
			values.add(solver.solve(state, angle, model));
		}
		return new TaylorMaccollTable(machAxis, coneAngleAxis, model.gammaValue(), values);
	}
	public TaylorMaccollSolution atNode(int machIndex, int coneIndex) { return values.get(machIndex * coneAngleAxis.length + coneIndex); }
	public double[] machAxis() { return machAxis.clone(); } public double[] coneAngleAxis() { return coneAngleAxis.clone(); }
	public double gamma() { return gamma; }
}

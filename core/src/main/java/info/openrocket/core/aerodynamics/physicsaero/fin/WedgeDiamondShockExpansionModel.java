package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;

/** Independent upper/lower surface shock-expansion marches for piecewise-linear sections. */
public final class WedgeDiamondShockExpansionModel {
	public static final String METHOD_ID = "WEDGE_DIAMOND_SHOCK_EXPANSION_V1";

	public Result evaluate(GasState upstream, ThermodynamicModel model, double incidenceRad,
			double[] upperPanelAnglesRad, double[] lowerPanelAnglesRad, double[] chordFractions,
			double stripAreaM2) {
		if (upstream.mach() <= 1 || stripAreaM2 <= 0 || upperPanelAnglesRad.length == 0
				|| upperPanelAnglesRad.length != lowerPanelAnglesRad.length
				|| upperPanelAnglesRad.length != chordFractions.length) throw new IllegalArgumentException("invalid section march input");
		double sum = 0; for (double f : chordFractions) { if (f <= 0) throw new IllegalArgumentException("invalid panel fraction"); sum += f; }
		if (Math.abs(sum - 1) > 1e-9) throw new IllegalArgumentException("panel fractions must sum to one");
		SurfaceMarch upper = march(upstream, model, incidenceRad, upperPanelAnglesRad, chordFractions);
		SurfaceMarch lower = march(upstream, model, -incidenceRad, lowerPanelAnglesRad, chordFractions);
		if (!upper.valid() || !lower.valid()) return new Result(false, 0, 0, upper, lower, METHOD_ID);
		double normal = 0, axial = 0;
		for (int i = 0; i < chordFractions.length; i++) {
			double area = stripAreaM2 * chordFractions[i];
			double upperGauge = upper.panelStates().get(i).pressurePa() - upstream.pressurePa();
			double lowerGauge = lower.panelStates().get(i).pressurePa() - upstream.pressurePa();
			normal += (lowerGauge - upperGauge) * area;
			// Keep the signed panel slope.  The rear face of a diamond section has
			// both a negative gauge pressure and an aft-facing surface normal, so
			// its axial contribution is positive.  Taking abs(tan(theta)) made that
			// expansion load cancel the leading-face compression load and collapsed
			// the O(theta^2) Ackeret wave drag to a spurious higher-order residual.
			axial += (upperGauge * Math.tan(upperPanelAnglesRad[i])
					+ lowerGauge * Math.tan(lowerPanelAnglesRad[i])) * area;
		}
		return new Result(true, normal, Math.max(0, axial), upper, lower, METHOD_ID);
	}

	private static SurfaceMarch march(GasState initial, ThermodynamicModel model, double flowAngle,
			double[] panelAngles, double[] fractions) {
		GasState state = initial; double previous = flowAngle; List<GasState> states = new ArrayList<>();
		List<String> events = new ArrayList<>();
		for (int i = 0; i < panelAngles.length; i++) {
			double turn = panelAngles[i] - previous;
			try {
				if (turn > 1e-12) {
					ShockSolution shock = ObliqueShockCalculator.solve(state, turn, ShockSolution.Branch.WEAK, model);
					if (shock.attachment() == ShockSolution.Attachment.DETACHED) return new SurfaceMarch(false, states, append(events, "DETACHED"));
					state = shock.downstream(); events.add("COMPRESSION");
				} else if (turn < -1e-12) {
					state = PrandtlMeyerCalculator.expand(state, -turn, model).downstream(); events.add("EXPANSION");
				} else events.add("NO_TURN");
			} catch (GasDynamicsException ex) { return new SurfaceMarch(false, states, append(events, "INVALID")); }
			states.add(state); previous = panelAngles[i];
		}
		return new SurfaceMarch(true, states, events);
	}
	private static List<String> append(List<String> values, String value) { List<String> copy = new ArrayList<>(values); copy.add(value); return copy; }

	public record SurfaceMarch(boolean valid, List<GasState> panelStates, List<String> events) {
		public SurfaceMarch { panelStates = List.copyOf(panelStates); events = List.copyOf(events); }
	}
	public record Result(boolean valid, double normalForceN, double axialForceN, SurfaceMarch upper,
			SurfaceMarch lower, String methodId) {}
}

package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;

public final class NormalShockCalculator {
	private NormalShockCalculator() {}
	public static ShockSolution solve(GasState upstream, ThermodynamicModel model) {
		double m1 = upstream.mach();
		double g = model.gamma(upstream.temperatureK());
		if (!(m1 > 1)) throw new GasDynamicsException("normal shock requires Mach > 1");
		double m12 = m1 * m1;
		double m22 = (1 + 0.5 * (g - 1) * m12) / (g * m12 - 0.5 * (g - 1));
		double pr = 1 + 2 * g / (g + 1) * (m12 - 1);
		double rr = (g + 1) * m12 / ((g - 1) * m12 + 2);
		double tr = pr / rr;
		double t2 = upstream.temperatureK() * tr;
		GasState down = new GasState(Math.sqrt(m22), upstream.pressurePa() * pr, t2,
				upstream.densityKgM3() * rr, Math.sqrt(m22) * model.speedOfSound(t2));
		TotalState total1 = IsentropicRelations.totalState(upstream, model);
		TotalState idealTotal2 = IsentropicRelations.totalState(down, model);
		double ptr = Math.pow((g + 1) * m12 / ((g - 1) * m12 + 2), g / (g - 1))
				* Math.pow((g + 1) / (2 * g * m12 - (g - 1)), 1 / (g - 1));
		TotalState total2 = new TotalState(total1.pressurePa() * ptr, total1.temperatureK(), idealTotal2.densityKgM3());
		double entropy = -model.gasConstant() * Math.log(ptr);
		double residual = Math.abs(total2.temperatureK() / total1.temperatureK() - 1);
		return new ShockSolution(upstream, down, total1, total2, Math.PI / 2, 0,
				ShockSolution.Branch.NORMAL, ShockSolution.Attachment.ATTACHED, entropy, residual);
	}
}

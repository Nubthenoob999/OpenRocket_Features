package info.openrocket.core.aerodynamics.physicsaero.powered;

import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/**
 * RASAero II v1.0.2.0 empirical power-on nozzle/base-drag correction.
 *
 * <p>The RASAero input contract supplies nozzle exit diameter without chamber
 * or exit-plane thermodynamics.  Its power-on axial correction is therefore
 * expressed only through Mach number and exit-to-reference area ratio:</p>
 *
 * <pre>delta CD = -F(M) * A_exit / A_reference</pre>
 *
 * <p>The negative sign is base-drag relief from the operating nozzle.  Keeping
 * this model separate prevents a nozzle-geometry-only CDX import from being
 * treated as the resolved rocket-jet state used by the NACA RM L54D27 model.</p>
 */
public final class RasaeroPoweredNozzleDragModel {
	public static final String METHOD_ID =
			"RASAERO_II_1_0_2_POWER_ON_NOZZLE_DRAG_V1";

	public PoweredFlowResult evaluate(AeroGeometry geometry, double mach,
			PoweredFlowState powered) {
		if (geometry == null || powered == null || !Double.isFinite(mach)
				|| mach < 0) {
			throw new IllegalArgumentException("INVALID_RASAERO_POWERED_NOZZLE_STATE");
		}
		if (powered.resolution()
				!= PoweredFlowState.Resolution.NOZZLE_GEOMETRY_ONLY) {
			throw new IllegalArgumentException(
					"RASAERO_POWERED_NOZZLE_REQUIRES_GEOMETRY_ONLY_STATE");
		}
		double referenceArea = geometry.references().referenceAreaM2();
		double delta = -powered.poweredFraction() * coefficient(mach)
				* powered.nozzleExitAreaM2() / referenceArea;
		return new PoweredFlowResult(delta, delta, 0, 0,
				List.of(METHOD_ID),
				List.of("RASAERO_NOZZLE_GEOMETRY_ONLY_POWER_ON_CORRELATION",
						"NOZZLE_EXIT_TO_REFERENCE_AREA_SCALING",
						"POWER_ON_BASE_DRAG_RELIEF"));
	}

	static double coefficient(double mach) {
		if (!Double.isFinite(mach) || mach < 0) {
			throw new IllegalArgumentException("INVALID_MACH");
		}
		if (mach < 0.58) {
			return 0.0547;
		}
		if (mach < 0.925) {
			return 0.0547 + (mach - 0.58) * 0.111304348;
		}
		if (mach < 1.0) {
			return 0.0931 + (mach - 0.925) * 0.901333333;
		}
		if (mach < 1.1935) {
			return 0.1607 + (mach - 1.0) * -0.066666667;
		}
		if (mach < 1.97) {
			return 0.1478;
		}
		if (mach < 2.45) {
			return 0.1478 + (mach - 1.97) * 0.037916667;
		}
		if (mach < 3.0) {
			return 0.166 + (mach - 2.45) * -0.077090909;
		}
		if (mach < 3.85) {
			return 0.1236 + (mach - 3.0) * -0.039529412;
		}
		if (mach < 4.387) {
			return 0.09 + (mach - 3.85) * -0.025512104;
		}
		double hypersonic = hypersonicCoefficient(mach);
		if (mach < 7.5) {
			return (-0.13413327894395483 * (mach - 4.387)
					+ 1.4175568973525314) * hypersonic;
		}
		return hypersonic;
	}

	private static double hypersonicCoefficient(double mach) {
		double pressureCoefficient = 2.0 / (1.4 * mach * mach)
				* (0.7747226539790469 * Math.pow(1.0 / mach, 2.8)
						* ((2.8 * mach * mach - 0.4) / 2.4) - 1.0);
		return -pressureCoefficient;
	}
}

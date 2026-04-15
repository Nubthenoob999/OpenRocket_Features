package info.openrocket.core.aerodynamics.rom;

import java.util.List;

public class InducedDragModel {

	private static volatile double protuberanceFactor = 1.02;

	/**
	 * AoA-dependent drag increment.
	 * OpenRocket scaling: Cd(alpha) ~= Cd0 * (1 + k*(alpha/alpha_ref)^2)
	 * where k and alpha_ref are empirical from Barrowman body lift data.
	 *
	 * For fin-dominated stability: uses simplified induced drag from
	 * lift-induced mechanism: deltaCd = Cl^2 / (pi * AR * e)
	 * where AR is fin aspect ratio and e ~= 0.9 span efficiency.
	 *
	 * @param alphaRad angle of attack in radians
	 */
	public static double cdInduced(double alphaRad, double mach,
							   RomGeometryParameters g) {
		List<RomGeometryParameters.FinGeom> finSets = g.resolvedFinSets();
		if (alphaRad < 1e-6 || finSets.isEmpty()) {
			return 0.0;
		}
		double e = 0.9; // span efficiency

		// Smooth bounded subsonic compressibility factor.
		// Blend from corrected subsonic behavior (M<=0.8) to unity by M=1.2.
		double compressibilityFactor = 1.0;
		if (mach <= 0.8) {
			double beta2 = Math.max(1.0 - mach * mach, 0.35);
			compressibilityFactor = 1.0 / Math.sqrt(beta2);
		} else if (mach < 1.2) {
			double beta2At08 = Math.max(1.0 - 0.8 * 0.8, 0.35);
			double subsonicAt08 = 1.0 / Math.sqrt(beta2At08);
			double t = (mach - 0.8) / 0.4;
			double smooth = t * t * (3.0 - 2.0 * t); // smoothstep
			compressibilityFactor = subsonicAt08 * (1.0 - smooth) + smooth;
		}

		double cd_induced = 0.0;
		for (RomGeometryParameters.FinGeom finSet : finSets) {
			double chordMean = finSet.meanChord();
			double ar = 2.0 * finSet.span() / Math.max(chordMean, 1.0e-9);
			if (!(ar > 0.0)) {
				continue;
			}
			double cNaFin = 2.0 * Math.PI / (1.0 + 2.0 / ar);
			double cl = cNaFin * alphaRad * (finSet.totalPlanformArea() / g.referenceArea);
			cl *= compressibilityFactor;
			cd_induced += cl * cl / (Math.PI * ar * e);
		}

		// Add body AoA drag (sin^2 approximation for cross-flow)
		double cd_body_aoa = 0.075 * Math.sin(alphaRad) * Math.sin(alphaRad);

		return cd_induced + cd_body_aoa;
	}

	/**
	 * Protuberance correction factor (rail buttons, camera mounts, etc.).
	 * Applied as a multiplier: Cd_total *= protuberanceFactor().
	 * Default Kf = 1.04 (4% increment, Barrowman standard).
	 * Can be parameterized from user input in Phase 4.
	 */
	public static double protuberanceFactor() {
		return protuberanceFactor;
	}

	public static void setProtuberanceFactor(double factor) {
		protuberanceFactor = Math.max(1.0, Math.min(1.20, factor));
	}
}

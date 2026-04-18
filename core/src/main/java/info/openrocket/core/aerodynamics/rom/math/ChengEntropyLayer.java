package info.openrocket.core.aerodynamics.rom.math;

import info.openrocket.core.aerodynamics.rom.geometry.AxisymmetricGeometry;

/**
 * Blunt-nose entropy-layer swallowing correction (Cheng 1961 / Lees-Probstein).
 *
 * <p>Accounts for the bow-shock entropy layer adjacent to a blunt nose tip and
 * its gradual swallowing by the growing boundary layer. This correction reduces
 * the effective edge-Mach number and modifies the Cf multiplier near the nose.
 *
 * <p>Reference: Cheng (1961); Lees &amp; Probstein blunt-body entropy layer model.
 */
public final class ChengEntropyLayer {

    private ChengEntropyLayer() {}

    /**
     * Returns true if the entropy-layer correction is applicable to this segment.
     *
     * <p>Applicable when the fore radius is large relative to the segment length
     * (blunt nose) and the component type indicates a nose cone.
     *
     * @param noseSeg  nose segment geometry
     * @return true if blunt-nose entropy layer correction applies
     */
    public static boolean isApplicable(AxisymmetricGeometry noseSeg) {
        if (noseSeg == null) {
            return false;
        }
        double length = noseSeg.getXEnd() - noseSeg.getXStart();
        if (length <= 0.0) {
            return false;
        }
        // Blunt when fore-radius-to-length ratio > 0.15 (empirical threshold)
        double bluntnessRatio = noseSeg.getForeRadius() / Math.max(1e-9, length);
        return bluntnessRatio > 0.15 && noseSeg.getForeRadius() > 1e-4;
    }

    /**
     * Bow-shock stand-off distance (m) for a blunt nose at the given conditions.
     *
     * @param R_nose    nose radius (m)
     * @param mach_inf  freestream Mach number
     * @param gamma     ratio of specific heats
     * @return bow-shock stand-off distance (m)
     */
    public static double bowShockStandoff(double R_nose, double mach_inf, double gamma) {
        if (mach_inf <= 1.0 || R_nose <= 0.0) {
            return 0.0;
        }
        // Billig (1967) approximation: delta_s / R_nose ≈ 0.78 * (rho_inf / rho_2)
        // Normal-shock density ratio:
        double mn2 = mach_inf * mach_inf;
        double rhoRatio = ((gamma + 1.0) * mn2) / ((gamma - 1.0) * mn2 + 2.0);
        return 0.78 * R_nose / Math.max(1.0, rhoRatio);
    }

    /**
     * Distance (m) at which the entropy layer is fully swallowed by the BL.
     *
     * @param R_nose       nose radius (m)
     * @param Re_inf_per_m unit Reynolds number (1/m)
     * @param gamma        ratio of specific heats
     * @return swallowing distance (m)
     */
    public static double swallowingDistance(double R_nose, double Re_inf_per_m, double gamma) {
        if (R_nose <= 0.0 || Re_inf_per_m <= 0.0) {
            return 1.0;
        }
        // Empirical: x_sw ~ f(gamma) * Re_inf(R_nose) * R_nose
        double fGamma = 0.25 * (gamma + 1.0);
        double Re_nose = Re_inf_per_m * R_nose;
        return fGamma * Re_nose * R_nose;
    }

    /**
     * Corrected edge-Mach number accounting for the entropy layer at station x.
     *
     * @param x               axial station (m)
     * @param x_sw            swallowing distance (m)
     * @param M_e_sharp       edge Mach for equivalent sharp-nose geometry
     * @param M_e_stagnation  stagnation Mach (≈ 0 at blunt tip)
     * @return corrected edge Mach
     */
    public static double correctedEdgeMach(double x, double x_sw,
                                           double M_e_sharp, double M_e_stagnation) {
        if (x_sw <= 0.0) {
            return M_e_sharp;
        }
        double t = Math.min(1.0, x / x_sw);
        // Smooth ramp from stagnation value to sharp-nose value
        double w = t * t * (3.0 - 2.0 * t); // smoothstep
        return M_e_stagnation + w * (M_e_sharp - M_e_stagnation);
    }

    /**
     * Skin-friction multiplier to account for entropy-layer thinning near the nose.
     *
     * @param x              axial station (m)
     * @param x_sw           swallowing distance (m)
     * @param M_e_sharp      edge Mach for sharp-nose geometry
     * @param M_e_corrected  entropy-layer corrected edge Mach
     * @param gamma          ratio of specific heats
     * @return multiplier applied to Cf (typically &lt; 1 near the nose)
     */
    public static double cfMultiplier(double x, double x_sw, double M_e_sharp,
                                      double M_e_corrected, double gamma) {
        if (x_sw <= 0.0 || M_e_sharp <= 0.0) {
            return 1.0;
        }
        // Cf scales approximately with (M_e_corrected / M_e_sharp)^(gamma-1)
        double ratio = M_e_corrected / Math.max(1e-6, M_e_sharp);
        return Math.pow(Math.max(0.1, ratio), gamma - 1.0);
    }
}

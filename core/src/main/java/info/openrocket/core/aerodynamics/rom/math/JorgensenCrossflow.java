package info.openrocket.core.aerodynamics.rom.math;

import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;

/**
 * Jorgensen nonlinear crossflow body-lift model.
 *
 * <p>Provides a semi-empirical increment to the normal-force coefficient at
 * moderate-to-large angles of attack (alpha &gt; ~5°) where the slender-body
 * linear theory under-predicts body lift due to viscous crossflow separation.
 *
 * <p>Reference: Jorgensen (1977), NASA TM X-73305.
 */
public final class JorgensenCrossflow {

    private JorgensenCrossflow() {}

    /**
     * Crossflow drag coefficient as a function of crossflow Mach number.
     *
     * <p>Represents the 2D cylinder drag in the crossflow plane.
     *
     * @param mach_crossflow  crossflow Mach number = M_inf * sin(alpha)
     * @return crossflow drag coefficient Cd_c
     */
    public static double crossflowDragCoeff(double mach_crossflow) {
        // Subsonic cylinder: Cd_c ~ 1.2 (bluff body)
        // Supersonic: Cd_c decreases with Mach
        if (mach_crossflow < 0.7) {
            return 1.2;
        }
        if (mach_crossflow < 1.0) {
            // Transonic ramp
            return 1.2 - 0.4 * (mach_crossflow - 0.7) / 0.3;
        }
        // Supersonic: Cd_c ~ 1 / sqrt(Mc^2 - 1) + 0.4 (empirical)
        double beta = Math.sqrt(Math.max(0.01, mach_crossflow * mach_crossflow - 1.0));
        return 0.4 + 0.6 / beta;
    }

    /**
     * Normal-force increment from nonlinear Jorgensen crossflow.
     *
     * @param alpha      angle of attack (rad)
     * @param mach_inf   freestream Mach number
     * @param A_planform planform (projected side) area of the body (m^2)
     * @param A_ref      reference area (m^2)
     * @param eta        efficiency factor (0-1; typically 0.9 for rockets)
     * @return delta_CN from crossflow body lift
     */
    public static double deltaCN(double alpha, double mach_inf,
                                  double A_planform, double A_ref, double eta) {
        if (Math.abs(alpha) < 1e-6 || A_planform <= 0.0 || A_ref <= 0.0) {
            return 0.0;
        }
        double sinAlpha = Math.sin(alpha);
        double mach_c = mach_inf * Math.abs(sinAlpha);
        double Cd_c = crossflowDragCoeff(mach_c);
        // Jorgensen: delta_CN = eta * Cd_c * sin^2(alpha) * cos(alpha) * A_planform / A_ref
        return eta * Cd_c * sinAlpha * sinAlpha * Math.cos(alpha) * A_planform / A_ref;
    }

    /**
     * Planform (side-projected) area of the body meridian profile.
     *
     * @param profile  geometry features representing the body
     * @return planform area (m^2)
     */
    public static double planformArea(GeometryFeatures profile) {
        if (profile == null) {
            return 0.0;
        }
        double[] x = profile.getXStations();
        double[] r = profile.getRadiusStations();
        if (x.length < 2) {
            return 0.0;
        }
        // Trapezoidal integration of 2*r(x) dx (projected onto x-y plane)
        double area = 0.0;
        for (int i = 1; i < Math.min(x.length, r.length); i++) {
            double dx = x[i] - x[i - 1];
            double rMid = 0.5 * (r[i - 1] + r[i]);
            area += 2.0 * Math.max(0.0, rMid) * Math.max(0.0, dx);
        }
        return area;
    }

    /**
     * Returns true if the Jorgensen crossflow model is applicable.
     *
     * @param alpha          angle of attack (rad)
     * @param noseHalfAngle  nose half-angle (rad)
     * @return true when crossflow correction should be applied
     */
    public static boolean isApplicable(double alpha, double noseHalfAngle) {
        // Applicable at moderate alpha; not for very blunt noses
        return Math.abs(alpha) > Math.toRadians(5.0)
                && noseHalfAngle < Math.toRadians(35.0);
    }

    /**
     * Self-check: returns true if inputs are physically reasonable for model use.
     *
     * @param alpha  angle of attack (rad)
     * @param mach   freestream Mach number
     * @return true if inputs are within model validity range
     */
    public static boolean selfCheck(double alpha, double mach) {
        return Double.isFinite(alpha) && Double.isFinite(mach)
                && mach >= 0.0 && Math.abs(alpha) <= Math.toRadians(60.0);
    }
}

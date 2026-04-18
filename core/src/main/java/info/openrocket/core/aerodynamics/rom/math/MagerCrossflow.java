package info.openrocket.core.aerodynamics.rom.math;

/**
 * Mager crossflow model for three-dimensional boundary-layer effects at
 * non-zero angle of attack.
 *
 * <p>Adds the 3D crossflow momentum term when the rocket is flying at an
 * off-axis attitude, augmenting the streamwise BL solution with a spanwise
 * shear component.
 *
 * <p>Reference: Mager (1952); "Generalization of boundary-layer momentum-integral
 * equations to three-dimensional flows including those of rotating system."
 */
public final class MagerCrossflow {

    private MagerCrossflow() {}

    /**
     * Crossflow angle at a meridian station on the body.
     *
     * @param alpha        angle of attack (rad)
     * @param phi          meridian azimuth angle (rad), 0 = windward
     * @param s            arc-length station along meridian (m)
     * @param totalLength  total body length (m)
     * @param mach         freestream Mach number
     * @return crossflow angle beta_w (rad)
     */
    public static double crossflowAngle(double alpha, double phi, double s,
                                        double totalLength, double mach) {
        if (totalLength <= 0.0 || Math.abs(alpha) < 1e-6) {
            return 0.0;
        }
        // Crossflow component is maximum at 90° to windward and decays
        // with compressibility and location along the body
        double crossComponent = Math.sin(alpha) * Math.sin(phi);
        double locationFactor = 1.0 - 0.5 * Math.min(1.0, s / totalLength);
        double compFactor = 1.0 / Math.max(1.0, Math.sqrt(1.0 + 0.5 * mach * mach));
        return crossComponent * locationFactor * compFactor;
    }

    /**
     * Augmented wall shear stress accounting for crossflow component.
     *
     * @param Cf_streamwise  streamwise skin friction coefficient
     * @param beta_w         crossflow angle (rad)
     * @return effective skin friction magnitude (Pythagorean addition)
     */
    public static double augmentedWallShear(double Cf_streamwise, double beta_w) {
        if (Math.abs(beta_w) < 1e-8) {
            return Cf_streamwise;
        }
        double Cf_cross = Cf_streamwise * Math.abs(Math.tan(beta_w));
        return Math.sqrt(Cf_streamwise * Cf_streamwise + Cf_cross * Cf_cross);
    }

    /**
     * Crossflow momentum term added to the streamwise momentum integral.
     *
     * @param theta   momentum thickness (m)
     * @param beta_w  crossflow angle (rad)
     * @param Hk      kinematic shape factor
     * @param ue      edge velocity (m/s)
     * @param due_ds  streamwise edge-velocity gradient (1/s)
     * @return additional d(theta)/ds contribution from crossflow
     */
    public static double crossflowMomentumTerm(double theta, double beta_w,
                                               double Hk, double ue, double due_ds) {
        if (Math.abs(beta_w) < 1e-8 || theta <= 0.0) {
            return 0.0;
        }
        // Mager approximation: extra entrainment from crossflow curvature
        double tanBeta = Math.tan(beta_w);
        return theta * tanBeta * tanBeta * Math.abs(due_ds) / Math.max(1e-9, ue);
    }

    /**
     * Returns true if the Mager crossflow model is applicable for the
     * given flight conditions.
     *
     * <p>Model is valid for moderate alpha and not-too-blunt noses.
     *
     * @param alpha          angle of attack (rad)
     * @param noseHalfAngle  nose half-angle (rad)
     * @return true if crossflow correction should be applied
     */
    public static boolean isValid(double alpha, double noseHalfAngle) {
        // Applicable for 0 < alpha < 20 deg and moderately slender noses
        return Math.abs(alpha) > 1e-6
                && Math.abs(alpha) < Math.toRadians(20.0)
                && noseHalfAngle < Math.toRadians(30.0);
    }
}

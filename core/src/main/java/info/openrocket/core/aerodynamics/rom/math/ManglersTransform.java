package info.openrocket.core.aerodynamics.rom.math;

/**
 * Mangler's transformation for axisymmetric boundary layers.
 *
 * <p>Maps the axisymmetric boundary-layer equations to an equivalent
 * two-dimensional form via the Mangler radius-dependent body-scaling.
 * This allows planar BL solvers (Thwaites, Head) to be applied on
 * bodies of revolution with proper area-effect corrections.
 *
 * <p>The class name follows the Phase II naming normalization:
 * {@code ManglersTransform} (class name) while the method documentation
 * retains the "Mangler" terminology for reference.
 *
 * <p>Reference: Mangler (1948); White "Viscous Fluid Flow" Ch. 7.
 */
public final class ManglersTransform {

    private ManglersTransform() {}

    /**
     * Transform axial arc-length coordinate s to an equivalent 2D coordinate
     * using the Mangler radius scaling.
     *
     * <p>x_2D(s) = (1/L^2) * integral_0^s r(s')^2 ds'
     *
     * @param sArray   axial arc-length stations (m), monotonically increasing
     * @param rArray   body radius at each station (m)
     * @param L        reference length (m)
     * @return         2D equivalent arc-length array
     */
    public static double[] toEquivalent2D(double[] sArray, double[] rArray, double L) {
        int n = Math.min(sArray.length, rArray.length);
        double[] s2d = new double[n];
        if (n == 0 || L <= 0.0) {
            return s2d;
        }
        double Lsq = L * L;
        double integral = 0.0;
        s2d[0] = 0.0;
        for (int i = 1; i < n; i++) {
            double ds = sArray[i] - sArray[i - 1];
            double rMid = 0.5 * (rArray[i - 1] + rArray[i]);
            integral += rMid * rMid * Math.max(0.0, ds);
            s2d[i] = integral / Lsq;
        }
        return s2d;
    }

    /**
     * Scale the streamwise velocity gradient from axisymmetric to equivalent-2D.
     *
     * <p>due_ds_2D = due_ds_axi * (L / r)^2
     *
     * @param due_ds_axi  axisymmetric edge-velocity gradient (1/s)
     * @param r           local body radius (m)
     * @param L           reference length (m)
     * @return equivalent 2D velocity gradient
     */
    public static double transformVelocityGradient(double due_ds_axi, double r, double L) {
        if (r <= 0.0 || L <= 0.0) {
            return due_ds_axi;
        }
        double scale = (L / r) * (L / r);
        return due_ds_axi * scale;
    }

    /**
     * Inverse transform: convert 2D momentum thickness back to axisymmetric value.
     *
     * <p>theta_axi = theta_2D * (L / r)^(2/3)   [Mangler inverse approximation]
     *
     * @param theta2D  momentum thickness in 2D equivalent coordinates (m)
     * @param r        local body radius (m)
     * @param L        reference length (m)
     * @return axisymmetric momentum thickness (m)
     */
    public static double inverseTransformTheta(double theta2D, double r, double L) {
        if (r <= 0.0 || L <= 0.0) {
            return theta2D;
        }
        return theta2D * Math.pow(L / Math.max(1e-9, r), 2.0 / 3.0);
    }

    /**
     * Compute the Mangler-equivalent displacement thickness.
     *
     * @param delta_star_2D  2D displacement thickness (m)
     * @param r              local body radius (m)
     * @param L              reference length (m)
     * @return axisymmetric displacement thickness (m)
     */
    public static double inverseTransformDeltaStar(double delta_star_2D, double r, double L) {
        // Same scaling as theta for leading-order approximation
        return inverseTransformTheta(delta_star_2D, r, L);
    }
}

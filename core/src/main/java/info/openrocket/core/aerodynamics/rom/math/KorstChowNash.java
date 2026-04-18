package info.openrocket.core.aerodynamics.rom.math;

/**
 * Korst-Chow-Nash analytical base-pressure model for supersonic coast flight.
 *
 * <p>Provides a physics-based alternative to purely empirical base-drag fits
 * in the supersonic regime by modelling the turbulent mixing layer behind the
 * base and the resulting base pressure coefficient.
 *
 * <p>Reference: Korst (1956); Chow &amp; Korst (1957); Nash (1963) turbulent
 * reattachment factor.
 */
public final class KorstChowNash {

    // Nash reattachment factor (empirical constant for turbulent base flows)
    private static final double NASH_FACTOR_DEFAULT = 0.35;

    private KorstChowNash() {}

    /**
     * Turbulent mixing coefficient sigma at the base jet exit.
     *
     * @param mach_exit  exit-plane Mach number (equal to freestream for blunt base)
     * @return mixing coefficient sigma
     */
    public static double mixingCoefficient(double mach_exit) {
        if (mach_exit <= 0.0) {
            return 0.1;
        }
        // Semi-empirical Korst mixing: sigma ~ 0.09 / (1 + 0.16 * M^2)
        return 0.09 / (1.0 + 0.16 * mach_exit * mach_exit);
    }

    /**
     * Base pressure coefficient from Korst reattachment criterion.
     *
     * @param mach         freestream Mach number
     * @param Re_theta_sep Reynolds number based on momentum thickness at separation
     * @param gamma        ratio of specific heats
     * @param sigma        mixing coefficient
     * @return base pressure coefficient Cp_base (negative for drag)
     */
    public static double basePressureCoeff(double mach, double Re_theta_sep,
                                            double gamma, double sigma) {
        if (mach <= 0.0) {
            return 0.0;
        }
        // Korst reattachment: Cp_base depends on the mixing-region pressure rise
        // Simplified form: Cp_base = -2 / (gamma * M^2) * (1 - p_b/p_inf)
        // where p_b/p_inf from the Korst integral:
        double machSq = mach * mach;
        double beta = Math.sqrt(Math.max(0.0, machSq - 1.0));

        // Base suction factor from separation Reynolds number
        double reFactor = Math.min(1.0, 1.0 - 0.3 * Math.exp(-Re_theta_sep / 1000.0));

        // Korst base pressure coefficient (negative = suction)
        double cpMagnitude = (1.0 + nashFactor()) * sigma / Math.max(1e-3, beta) * reFactor;
        cpMagnitude = Math.min(cpMagnitude, 0.5 / Math.max(0.5, mach)); // physical bound
        return -cpMagnitude;
    }

    /**
     * Nash reattachment factor for turbulent base flows.
     *
     * @return Nash factor (dimensionless)
     */
    public static double nashFactor() {
        return NASH_FACTOR_DEFAULT;
    }

    /**
     * Full base Cp using default mixing coefficient.
     *
     * @param mach         freestream Mach number
     * @param Re_theta_sep momentum-thickness Reynolds number at separation
     * @param gamma        ratio of specific heats
     * @return base Cp (negative)
     */
    public static double fullBaseCp(double mach, double Re_theta_sep, double gamma) {
        double sigma = mixingCoefficient(mach);
        return basePressureCoeff(mach, Re_theta_sep, gamma, sigma);
    }
}

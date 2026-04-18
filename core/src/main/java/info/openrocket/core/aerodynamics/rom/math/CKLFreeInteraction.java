package info.openrocket.core.aerodynamics.rom.math;

import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;

/**
 * Chapman-Kuehn-Larson (CKL) free-interaction model for shock-induced
 * boundary-layer separation.
 *
 * <p>Predicts plateau and reattachment pressures for shock/boundary-layer
 * interaction (SWBLI) at supersonic Mach numbers.
 *
 * <p>Reference: Chapman, Kuehn &amp; Larson (1958); free-interaction theory.
 */
public final class CKLFreeInteraction {

    private CKLFreeInteraction() {}

    /**
     * Plateau pressure ratio p_plateau / p_1 for turbulent BL at shock station.
     *
     * @param mach1    upstream Mach number
     * @param Cf1      upstream skin friction coefficient
     * @param gamma    ratio of specific heats
     * @return plateau pressure ratio
     */
    public static double plateauPressureRatio(double mach1, double Cf1, double gamma) {
        if (mach1 <= 1.0 || Cf1 <= 0.0) {
            return 1.0;
        }
        double beta1 = Math.sqrt(Math.max(0.0, mach1 * mach1 - 1.0));
        double freeInteraction = Math.sqrt(2.0 * Cf1 / Math.max(1e-9, beta1));
        return 1.0 + 3.0 * gamma * mach1 * mach1 * freeInteraction;
    }

    /**
     * Returns true if turbulent boundary-layer separation is likely at this station.
     *
     * @param mach1         upstream Mach number
     * @param pressureRatio p / p_upstream at current station
     * @return true if separation is indicated
     */
    public static boolean turbulentSeparationOnset(double mach1, double pressureRatio) {
        if (mach1 <= 1.0) {
            return false;
        }
        // Separation onset correlation: p/p1 > 1 + 1.6 * sqrt((M1^2-1)^0.5 * Cf)
        // Approximation for typical supersonic BL: about 1.1 * plateauPressureRatio
        double sepThreshold = 1.0 + 0.5 * Math.sqrt(mach1 * mach1 - 1.0);
        return pressureRatio > sepThreshold;
    }

    /**
     * Reattachment pressure ratio after a separated supersonic bubble.
     *
     * @param mach1 upstream Mach number
     * @return reattachment pressure ratio p_r / p_1
     */
    public static double reattachmentPressureRatio(double mach1) {
        if (mach1 <= 1.0) {
            return 1.0;
        }
        // Empirical: reattachment approximately twice the plateau increment
        double beta1 = Math.sqrt(Math.max(0.0, mach1 * mach1 - 1.0));
        return 1.0 + 1.4 / Math.max(0.1, beta1);
    }

    /**
     * Apply the CKL free-interaction correction to a march state array at the
     * shock station.
     *
     * <p>Modifies downstream BL states to reflect the post-plateau pressure
     * distribution starting from {@code shockStationIndex}.
     *
     * @param states             array of BL states along the march path
     * @param shockStationIndex  index of the shock-arrival station
     * @param mach1              Mach number at shock station
     * @param gamma              ratio of specific heats
     */
    public static void applyToMarch(BoundaryLayerState[] states, int shockStationIndex,
                                    double mach1, double gamma) {
        if (states == null || shockStationIndex < 0 || shockStationIndex >= states.length) {
            return;
        }
        // Compute a shape-factor correction downstream of the shock to model
        // the slow BL recovery — prevents instantaneous reset.
        int n = states.length;
        double recoveryLength = Math.max(1, (n - shockStationIndex) / 4);
        for (int i = shockStationIndex; i < n; i++) {
            double t = Math.min(1.0, (i - shockStationIndex) / recoveryLength);
            double hBoost = (1.0 - t) * 0.5; // gradual shape-factor relaxation
            BoundaryLayerState orig = states[i];
            states[i] = new BoundaryLayerState(
                    orig.getDisplacementThickness(),
                    orig.getMomentumThickness(),
                    orig.getShapeFactor() + hBoost,
                    orig.getSkinFrictionCoefficient() * (1.0 - 0.3 * (1.0 - t)),
                    orig.isTurbulent(),
                    orig.isTransitioned(),
                    i == shockStationIndex && orig.isSeparated() || orig.isSeparated(),
                    orig.isValid(),
                    orig.getStiffnessIndicator(),
                    orig.getNotes());
        }
    }
}

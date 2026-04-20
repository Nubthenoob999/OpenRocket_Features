package info.openrocket.core.aerodynamics.rom.integration;

import info.openrocket.core.aerodynamics.rom.RomSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase II confidence scorer.
 *
 * <p>Computes a four-level confidence assessment for each ROM evaluation based
 * on flight regime, angle of attack, BL separation fraction, and calibration
 * overlay status. Drives the fallback blending policy defined in Section 8.
 */
public class ConfidenceScorer {

    // --- Locked AoA thresholds (Section 4 / 8) ---
    private static final double AOA_LOW_DEG       = 8.0;
    private static final double AOA_UNRELIABLE_DEG = 20.0;

    // --- Transonic Mach band ---
    private static final double M_TRANSONIC_LO = 0.85;
    private static final double M_TRANSONIC_HI = 1.15;

    // --- Separation threshold ---
    private static final double SEP_MEDIUM_THRESHOLD = 0.10;

    // --- Out-of-validity Mach ---
    private static final double M_MAX_VALID = 5.0;

    public enum ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW,
        UNRELIABLE
    }

    /**
     * Compute the confidence level for a ROM evaluation.
     *
     * @param mach                 freestream Mach number
     * @param alpha                angle of attack (rad)
     * @param noseHalfAngle        nose half-angle (rad)
     * @param calibrationActive    true if a calibration overlay is loaded and valid
     * @param separationFractions  per-pathline separation fractions (may be null/empty)
     * @return confidence level
     */
    public ConfidenceLevel score(double mach, double alpha, double noseHalfAngle,
                                 boolean calibrationActive,
                                 double[] separationFractions) {
        double alphaDeg = Math.toDegrees(Math.abs(alpha));

        // Hard UNRELIABLE triggers
        if (alphaDeg >= AOA_UNRELIABLE_DEG) {
            return ConfidenceLevel.UNRELIABLE;
        }
        if (mach > M_MAX_VALID) {
            return ConfidenceLevel.UNRELIABLE;
        }
        if (!checkSelfConsistency(separationFractions)) {
            return ConfidenceLevel.UNRELIABLE;
        }

        // Hard LOW triggers
        if (alphaDeg >= AOA_LOW_DEG) {
            return ConfidenceLevel.LOW;
        }
        if (isTransonic(mach) && !calibrationActive) {
            // Default LOW; elevate to MEDIUM only if all checks are clean
            if (separationFraction(separationFractions) < SEP_MEDIUM_THRESHOLD
                    && !hasHighSeparationFraction(separationFractions)) {
                return ConfidenceLevel.MEDIUM;
            }
            return ConfidenceLevel.LOW;
        }

        // MEDIUM triggers
        if (hasHighSeparationFraction(separationFractions)) {
            return ConfidenceLevel.MEDIUM;
        }

        // Transonic with calibration → HIGH allowed
        if (isTransonic(mach) && calibrationActive) {
            return ConfidenceLevel.HIGH;
        }

        return ConfidenceLevel.HIGH;
    }

    /**
     * Generate human-readable warning strings for active confidence conditions.
     *
     * @param mach                 freestream Mach number
     * @param alpha                angle of attack (rad)
     * @param noseHalfAngle        nose half-angle (rad)
     * @param separationFractions  per-pathline separation fractions
     * @return list of warning strings (empty if none)
     */
    public String[] generateWarnings(double mach, double alpha, double noseHalfAngle,
                                     boolean calibrationActive,
                                     double[] separationFractions) {
        List<String> warnings = new ArrayList<>();
        String w;

        w = checkTransonicWithoutCalibration(mach, calibrationActive);
        if (w != null) warnings.add(w);

        w = checkLeeSideSeparation(alpha, noseHalfAngle);
        if (w != null) warnings.add(w);

        w = checkHighSeparationFractionMsg(separationFractions);
        if (w != null) warnings.add(w);

        return warnings.toArray(new String[0]);
    }

    /**
     * Returns true when fallback blending should be engaged.
     *
     * <p>Per Section 8.3: enter fallback at UNRELIABLE.
     *
     * @param level   current confidence level
     * @param config  ROM settings (unused in base policy; reserved for override)
     * @return true if fallback should be blended in
     */
    public boolean shouldFallback(ConfidenceLevel level, RomSettings config) {
        return level == ConfidenceLevel.UNRELIABLE;
    }

    // --- private check methods ---

    private String checkTransonicWithoutCalibration(double mach, boolean calibrationActive) {
        if (isTransonic(mach) && !calibrationActive) {
            return String.format("Transonic regime (M=%.2f) without calibration overlay – confidence LOW", mach);
        }
        return null;
    }

    private String checkLeeSideSeparation(double alpha, double noseHalfAngle) {
        double alphaDeg = Math.toDegrees(Math.abs(alpha));
        if (alphaDeg > AOA_LOW_DEG) {
            return String.format("High angle of attack (%.1f°) – lee-side separation likely", alphaDeg);
        }
        return null;
    }

    private String checkHighSeparationFractionMsg(double[] separationFractions) {
        double frac = separationFraction(separationFractions);
        if (frac > SEP_MEDIUM_THRESHOLD) {
            return String.format("Separation fraction %.0f%% exceeds 10%% threshold", frac * 100.0);
        }
        return null;
    }

    private static boolean isTransonic(double mach) {
        return mach >= M_TRANSONIC_LO && mach <= M_TRANSONIC_HI;
    }

    private static double separationFraction(double[] fractions) {
        if (fractions == null || fractions.length == 0) return 0.0;
        double sum = 0.0;
        for (double f : fractions) sum += f;
        return sum / fractions.length;
    }

    private static boolean hasHighSeparationFraction(double[] fractions) {
        return separationFraction(fractions) > SEP_MEDIUM_THRESHOLD;
    }

    private static boolean checkSelfConsistency(double[] fractions) {
        // If all pathlines are separated, flag UNRELIABLE
        if (fractions == null || fractions.length == 0) return true;
        double sep = separationFraction(fractions);
        return sep < 0.95; // UNRELIABLE if >95% separated
    }
}

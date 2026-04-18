package info.openrocket.core.aerodynamics.rom.integration;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.rom.flow.FlowRegime;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.integration.ConfidenceScorer.ConfidenceLevel;

/**
 * Full ROM output bundle for one simulation step.
 *
 * <p>Carries the blended {@link AerodynamicForces} returned to the simulation
 * stepper plus diagnostic fields needed for Phase III validation, export, and
 * the UI confidence indicator.
 */
public class RomOutputBundle {

    private final AerodynamicForces blendedForces;
    private final AerodynamicForces romForces;
    private final AerodynamicForces barrowmanForces;
    private final ConfidenceLevel confidenceLevel;
    private final String[] warnings;
    private final FlowRegime regime;
    private final FlowState flowState;
    private final GeometryFeatures geometry;
    private final double fallbackWeight;
    private final double separationFraction;
    private final boolean calibrationActive;

    public RomOutputBundle(AerodynamicForces blendedForces,
                           AerodynamicForces romForces,
                           AerodynamicForces barrowmanForces,
                           ConfidenceLevel confidenceLevel,
                           String[] warnings,
                           FlowRegime regime,
                           FlowState flowState,
                           GeometryFeatures geometry,
                           double fallbackWeight,
                           double separationFraction,
                           boolean calibrationActive) {
        this.blendedForces     = blendedForces;
        this.romForces         = romForces;
        this.barrowmanForces   = barrowmanForces;
        this.confidenceLevel   = confidenceLevel;
        this.warnings          = warnings != null ? warnings.clone() : new String[0];
        this.regime            = regime;
        this.flowState         = flowState;
        this.geometry          = geometry;
        this.fallbackWeight    = fallbackWeight;
        this.separationFraction = separationFraction;
        this.calibrationActive = calibrationActive;
    }

    /** Forces returned to the simulation stepper (blended ROM + Barrowman). */
    public AerodynamicForces getBlendedForces() { return blendedForces; }

    /** Pure ROM forces before fallback blending. */
    public AerodynamicForces getRomForces() { return romForces; }

    /** Barrowman reference forces used in fallback blend. */
    public AerodynamicForces getBarrowmanForces() { return barrowmanForces; }

    /** Phase II confidence level for this step. */
    public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }

    /** Active warning messages (empty array when none). */
    public String[] getWarnings() { return warnings.clone(); }

    /** Aerodynamic flow regime at this step. */
    public FlowRegime getRegime() { return regime; }

    /** Flow conditions at this step. */
    public FlowState getFlowState() { return flowState; }

    /** Geometry features used for this evaluation. */
    public GeometryFeatures getGeometry() { return geometry; }

    /** Barrowman fallback weight (0 = full ROM, 1 = full Barrowman). */
    public double getFallbackWeight() { return fallbackWeight; }

    /** Fraction of pathlines with detected separation. */
    public double getSeparationFraction() { return separationFraction; }

    /** True if a calibration overlay was applied. */
    public boolean isCalibrationActive() { return calibrationActive; }
}

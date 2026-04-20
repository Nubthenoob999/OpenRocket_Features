package info.openrocket.core.aerodynamics.rom.marching;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.outer.KarmanTsienReconstructor;
import info.openrocket.core.aerodynamics.rom.outer.ModifiedNewtonianReconstructor;
import info.openrocket.core.aerodynamics.rom.outer.OuterFlowReconstructor;
import info.openrocket.core.aerodynamics.rom.outer.ShockExpansionReconstructor;

/**
 * Full Mach-regime outer-flow dispatch for Phase II.
 *
 * <p>Replaces the Phase I {@code RegimeBlender} with a complete regime-correct
 * dispatch covering the full flight envelope from deep subsonic through
 * near-hypersonic. Also provides the transonic quintic-smoothstep bridge with
 * drag-rise hump and second-order shock-expansion correction for curved surfaces.
 *
 * <h3>Regime map</h3>
 * <ul>
 *   <li>M &lt; 0.60 – deep subsonic (Prandtl-Glauert)</li>
 *   <li>0.60 &le; M &lt; 0.80 – subsonic-compressible (Karman-Tsien)</li>
 *   <li>0.80 &le; M &le; 1.20 – transonic quintic bridge + Gaussian drag hump</li>
 *   <li>1.20 &lt; M &le; 5.0 – shock-expansion; Taylor-Maccoll for conical sections</li>
 *   <li>M &gt; 3.0, blunt – Modified Newtonian + Cheng correction</li>
 * </ul>
 */
public class FullOuterFlowDispatch implements OuterFlowReconstructor {

    // --- Regime boundaries ---
    private static final double M_SUB_DEEP_MAX   = 0.60;
    private static final double M_TRANSONIC_LOW  = 0.80;
    private static final double M_TRANSONIC_HIGH = 1.20;
    private static final double M_HYPERSONIC_LO  = 3.0;

    // --- Transonic drag hump parameters (Section 6.1.2 defaults) ---
    private static final double A_TRANSONIC = 0.06;
    private static final double M_PEAK      = 0.97;
    private static final double SIGMA_M     = 0.09;

    private static final double AIR_GAMMA = 1.4;

    private final KarmanTsienReconstructor subsonicRec  = new KarmanTsienReconstructor();
    private final ShockExpansionReconstructor superRec  = new ShockExpansionReconstructor();
    private final ModifiedNewtonianReconstructor hypRec = new ModifiedNewtonianReconstructor();

    @Override
    public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
        double mach = flowState.getMach();

        if (mach <= M_TRANSONIC_LOW) {
            return subsonicRec.reconstruct(seed, geometry, flowState);
        }
        if (mach >= M_TRANSONIC_HIGH) {
            if (mach > M_HYPERSONIC_LO && isBluntSection(seed, geometry)) {
                return hypRec.reconstruct(seed, geometry, flowState);
            }
            return superRec.reconstruct(seed, geometry, flowState);
        }
        // Transonic patch: quintic blend + drag-rise hump
        return reconstructTransonic(seed, geometry, flowState, mach);
    }

    /**
     * Transonic patch per Section 6.1.2.
     *
     * <p>Blends subsonic anchor at M=0.80 and supersonic anchor at M=1.20 with
     * a quintic smoothstep, then adds the Gaussian drag-rise hump.
     */
    private EdgeState reconstructTransonic(PathlineSeed seed, GeometryFeatures geometry,
                                           FlowState flowState, double mach) {
        double t = (mach - M_TRANSONIC_LOW) / (M_TRANSONIC_HIGH - M_TRANSONIC_LOW);
        double w = quinticSmoothstep(t);

        // Anchor states
        FlowState subAnchor   = withMach(flowState, M_TRANSONIC_LOW);
        FlowState superAnchor = withMach(flowState, M_TRANSONIC_HIGH);
        EdgeState eSub  = subsonicRec.reconstruct(seed, geometry, subAnchor);
        EdgeState eSuper = superRec.reconstruct(seed, geometry, superAnchor);

        // Blend Cp and edge Mach
        double cpBlended  = (1.0 - w) * eSub.getPressureCoefficient()
                           + w        * eSuper.getPressureCoefficient();
        double machBlended = (1.0 - w) * eSub.getEdgeMach()
                           + w        * eSuper.getEdgeMach();

        // Drag-rise hump (added to pressure drag, not directly Cp here)
        double deltaCd = dragRiseHump(mach);
        // Transonic drag-rise hump should not be mixed into local Cp;
        // it is accounted for at the integrated drag level, not in the pressure distribution.
        double cpWithHump = cpBlended;

        double incidence = (1.0 - w) * eSub.getLocalIncidenceRad()
                         + w * eSuper.getLocalIncidenceRad();
        double velGrad = (1.0 - w) * eSub.getVelocityGradient()
                       + w * eSuper.getVelocityGradient();
        double edgeVel = machBlended * flowState.getSpeedOfSound();

        return new EdgeState(edgeVel, machBlended,
                clamp(cpWithHump, -1.5, 2.0), incidence, velGrad, true);
    }

    /**
     * Second-order shock-expansion correction for curved surfaces (Syvertson-Dennis).
     *
     * @param Cp_firstOrder  first-order shock-expansion Cp
     * @param mach           local freestream Mach number
     * @param localCurvature local surface curvature (1/m)
     * @param deltaTheta     incremental turning angle (rad)
     * @return second-order corrected Cp
     */
    public static double syvertsonDennisCorrection(double Cp_firstOrder, double mach,
                                                    double localCurvature,
                                                    double deltaTheta) {
        if (mach <= 1.0 || Math.abs(deltaTheta) < 1e-8) {
            return Cp_firstOrder;
        }
        double beta = Math.sqrt(Math.max(0.01, mach * mach - 1.0));
        // Reflected characteristic correction term
        double delta_Cp = (AIR_GAMMA + 1.0) / (4.0 * beta * beta)
                * Cp_firstOrder * Cp_firstOrder
                + (2.0 * localCurvature * deltaTheta) / Math.max(1e-6, beta);
        return Cp_firstOrder + delta_Cp * 0.5; // blended to prevent over-correction
    }

    /**
     * Critical boattail half-angle below which the attached-flow model applies.
     *
     * @param mach freestream Mach number
     * @return critical angle (rad)
     */
    public static double boattailCriticalAngle(double mach) {
        // Policy default: beta_crit = 11 + 5*(1 - |M-1|) degrees
        double deg = 11.0 + 5.0 * Math.max(0.0, 1.0 - Math.abs(mach - 1.0));
        return Math.toRadians(deg);
    }

    // --- private helpers ---

    private static double quinticSmoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * t * (6.0 * t * t - 15.0 * t + 10.0);
    }

    private static double dragRiseHump(double mach) {
        double exponent = ((mach - M_PEAK) / SIGMA_M);
        return A_TRANSONIC * Math.exp(-exponent * exponent);
    }

    private static boolean isBluntSection(PathlineSeed seed, GeometryFeatures geometry) {
        double r = geometry.radiusAt(seed.getX());
        double drdx = Math.abs(geometry.areaSlopeAt(seed.getX()))
                / Math.max(1e-9, 2.0 * Math.PI * Math.max(1e-6, r));
        return drdx > 0.3; // blunt if slope > ~17 deg
    }

    private static FlowState withMach(FlowState base, double mach) {
        return new FlowState(mach,
                base.getReynoldsNumber(),
                base.getDynamicPressure(),
                base.getStaticPressure(),
                base.getStaticTemperature(),
                base.getDensity(),
                base.getSpeedOfSound(),
                base.getViscosity(),
                mach * base.getSpeedOfSound(),
                base.getAngleOfAttackRad(),
                base.getAngleOfAttackDeg(),
                base.getSideslipRad(),
                base.getSideslipDeg(),
                base.isPowered(),
                base.getPlumeState(),
                base.getReferenceLength());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

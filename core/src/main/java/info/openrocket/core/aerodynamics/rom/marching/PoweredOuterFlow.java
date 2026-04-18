package info.openrocket.core.aerodynamics.rom.marching;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.outer.OuterFlowReconstructor;

/**
 * Outer-flow reconstructor for powered-flight conditions.
 *
 * <p>Wraps the base {@link FullOuterFlowDispatch} and applies plume-induced
 * modifications to the aftbody and base edge states when a motor is burning.
 * The plume expansion angle modifies the effective boattail angle seen by the
 * outer flow, altering the downstream pressure distribution.
 */
public class PoweredOuterFlow implements OuterFlowReconstructor {

    private final FullOuterFlowDispatch baseDispatch = new FullOuterFlowDispatch();

    @Override
    public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
        EdgeState base = baseDispatch.reconstruct(seed, geometry, flowState);

        double plumeState = flowState.getPlumeState();
        if (plumeState < 1e-6) {
            return base; // coast, no plume correction
        }

        // Estimate effective plume expansion and modify aft Cp
        double mach = flowState.getMach();
        double baseCp = base.getPressureCoefficient();

        // Plume shielding: base pressure is partially filled by jet
        // Simple linear blend: powered Cp is less negative than coast
        double plumeCpBoost = plumeState * 0.15 / Math.max(1.0, mach);
        double modifiedCp = baseCp + plumeCpBoost;

        return new EdgeState(
                base.getEdgeVelocity(),
                base.getEdgeMach(),
                modifiedCp,
                base.getLocalIncidenceRad(),
                base.getVelocityGradient(),
                base.isValid());
    }
}

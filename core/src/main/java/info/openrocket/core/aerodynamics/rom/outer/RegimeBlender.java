package info.openrocket.core.aerodynamics.rom.outer;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowRegime;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.flow.RegimeSelector;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;

public class RegimeBlender implements OuterFlowReconstructor {
	private final RegimeSelector regimeSelector = new RegimeSelector();
	private final KarmanTsienReconstructor subsonic = new KarmanTsienReconstructor();
	private final ShockExpansionReconstructor supersonic = new ShockExpansionReconstructor();
	private final ModifiedNewtonianReconstructor hypersonic = new ModifiedNewtonianReconstructor();

	@Override
	public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
		FlowRegime regime = regimeSelector.select(flowState);
		if (regime == FlowRegime.SUBSONIC) {
			return subsonic.reconstruct(seed, geometry, flowState);
		}
		if (regime == FlowRegime.SUPERSONIC) {
			return supersonic.reconstruct(seed, geometry, flowState);
		}
		if (regime == FlowRegime.HYPERSONIC_LEANING) {
			return hypersonic.reconstruct(seed, geometry, flowState);
		}
		double blend = Math.max(0.0, Math.min(1.0, (flowState.getMach() - 0.80) / 0.40));
		EdgeState left = subsonic.reconstruct(seed, geometry, flowState);
		EdgeState right = supersonic.reconstruct(seed, geometry, flowState);
		return blend(left, right, blend);
	}

	private static EdgeState blend(EdgeState left, EdgeState right, double weight) {
		double inv = 1.0 - weight;
		return new EdgeState(
				inv * left.getEdgeVelocity() + weight * right.getEdgeVelocity(),
				inv * left.getEdgeMach() + weight * right.getEdgeMach(),
				inv * left.getPressureCoefficient() + weight * right.getPressureCoefficient(),
				inv * left.getLocalIncidenceRad() + weight * right.getLocalIncidenceRad(),
				inv * left.getVelocityGradient() + weight * right.getVelocityGradient(),
				left.isValid() && right.isValid());
	}
}

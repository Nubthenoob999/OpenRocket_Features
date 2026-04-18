package info.openrocket.core.aerodynamics.rom.outer;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

public class ModifiedNewtonianReconstructor implements OuterFlowReconstructor {
	private final TaylorMaccollTable taylorMaccollTable = new TaylorMaccollTable();

	@Override
	public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
		double radius = Math.max(1.0e-6, geometry.radiusAt(seed.getX()));
		double drdx = geometry.areaSlopeAt(seed.getX()) / Math.max(1.0e-6, 2.0 * Math.PI * radius);
		double localIncidence = Math.abs(flowState.getAngleOfAttackRad() + Math.atan(drdx));
		double cpMax = taylorMaccollTable.estimateCpMax(flowState.getMach());
		double cp = GasDynamics.modifiedNewtonianCp(Math.min(Math.PI / 2.0, localIncidence), cpMax);
		double edgeVelocity = flowState.getVelocity()
				* Math.sqrt(Math.max(0.05, 1.0 - 0.45 * cp / Math.max(1.0, cpMax)));
		double edgeMach = edgeVelocity / Math.max(1e-6, flowState.getSpeedOfSound());
		return new EdgeState(edgeVelocity, edgeMach, cp, localIncidence, -drdx * flowState.getVelocity(), true);
	}
}

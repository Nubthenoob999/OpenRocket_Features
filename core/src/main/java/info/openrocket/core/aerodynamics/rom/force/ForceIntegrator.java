package info.openrocket.core.aerodynamics.rom.force;

import java.util.List;

import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;
import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;

public class ForceIntegrator {
	public AerodynamicCoefficientAssembler integrate(GeometryFeatures geometry, FlowState flowState,
			List<PathlineSeed> seeds, List<EdgeState> edgeStates, List<BoundaryLayerState> boundaryLayerStates) {
		AerodynamicCoefficientAssembler assembler = new AerodynamicCoefficientAssembler();
		double refArea = Math.max(geometry.getReferenceArea(), 1e-6);
		double bodyLength = Math.max(geometry.getBodyLength(), geometry.getReferenceLength());
		double finAreaRatio = geometry.getTotalFinPlanformArea() / refArea;
		for (int i = 0; i < seeds.size(); i++) {
			PathlineSeed seed = seeds.get(i);
			EdgeState edgeState = edgeStates.get(i);
			BoundaryLayerState blState = boundaryLayerStates.get(i);
			double localWeight = seed.getAreaWeight() / refArea;
			double cp = edgeState.getPressureCoefficient();
			double cf = Math.max(0.0, blState.getSkinFrictionCoefficient());
			double axialPressure = Math.max(0.0, cp) * localWeight * 0.025;
			double axialFriction = cf * localWeight;
			double alphaEffect = flowState.getAngleOfAttackRad() * (1.0 + 0.35 * finAreaRatio);
			double normalContribution = cp * alphaEffect * localWeight * 0.35;
			double arm = (seed.getX() - 0.55 * bodyLength) / Math.max(geometry.getReferenceLength(), 1e-6);
			double momentContribution = normalContribution * arm - 0.15 * axialFriction * arm;
			assembler.addPressureContribution(new PressureContribution(
					seed.getComponentName(), axialPressure, normalContribution, momentContribution, seed.getX()));
			assembler.addFrictionContribution(new FrictionContribution(
					seed.getComponentName(), axialFriction, -0.05 * axialFriction * arm, seed.getX()));
		}
		return assembler;
	}
}

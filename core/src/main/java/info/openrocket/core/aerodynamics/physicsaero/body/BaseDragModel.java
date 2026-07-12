package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.coupling.BaseFlowBoundaryLayerAdapter;
import info.openrocket.core.util.Coordinate;

public final class BaseDragModel {
	private final BasePressureCorrelation correlation;
	public BaseDragModel() { this(new OpenRocketSupersonicBasePressureCorrelation()); }
	public BaseDragModel(BasePressureCorrelation correlation) { this.correlation = correlation; }
	public ForceContribution evaluate(ReferenceGeometry geometry, FlowCondition flow, String componentId) {
		BasePressureCorrelation.Validity validity = correlation.validity(flow.mach(), flow.powered());
		if (!validity.valid()) throw new IllegalArgumentException(validity.reason());
		double cp = correlation.basePressureCoefficient(flow.mach(), flow.thermodynamics().gamma(flow.atmosphere().temperatureK()));
		double forceX = -cp * flow.dynamicPressurePa() * geometry.exposedBaseAreaM2();
		PhysicalOwner owner = new PhysicalOwner(PhysicalTerm.BASE_PRESSURE_DRAG, OwnershipMode.REPLACES, "exposed-base", null);
		return new ForceContribution(componentId, owner, new MethodId(correlation.methodId()), new Coordinate(forceX, 0, 0),
				new Coordinate(), new Coordinate(geometry.vehicleLengthM(), 0, 0), "exposed-base", List.of(),
				1 - validity.uncertainty(), validity.uncertainty(), null);
	}
	/** Applies only the explicit, bounded terminal-BL adapter; exposed base area and base ownership are unchanged. */
	public ForceContribution evaluate(ReferenceGeometry geometry, FlowCondition flow, String componentId,
			BaseFlowBoundaryLayerAdapter.BaseFlowState boundaryLayer) {
		ForceContribution base=evaluate(geometry,flow,componentId);double factor=boundaryLayer.basePressureForceFactor();
		return new ForceContribution(base.componentId(),base.owner(),new MethodId(base.methodId().value()+"+BL_BASE_ADAPTER_V1"),
				new Coordinate(base.forceBodyN().x*factor,base.forceBodyN().y*factor,base.forceBodyN().z*factor),base.intrinsicMomentBodyNm(),
				base.applicationPointM(),base.regionId(),List.of("ACTUAL_EXPOSED_BASE_AREA","BOATTAIL_STATE_"+boundaryLayer.boattailState()),
				Math.min(base.confidence(),boundaryLayer.confidence()),Math.max(base.uncertainty(),1-boundaryLayer.confidence()),null);
	}
}

package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;
import info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.util.Coordinate;

/** Component-derived slender-body pressure/crossflow load at nonzero incidence. */
public final class BodyIncidenceLoadModel {
	public static final String METHOD_ID = "COMPONENT_BODY_INCIDENCE_LOAD_V1";

	public List<ForceContribution> evaluate(AeroGeometry geometry, FlowCondition flow) {
		if (Math.abs(flow.alphaRad()) < 1e-15 && Math.abs(flow.betaRad()) < 1e-15) return List.of();
		double referenceArea = geometry.references().referenceAreaM2();
		double frontalArea = Math.PI * Math.pow(0.5 * geometry.references().maximumBodyDiameterM(), 2);
		double slope = 2.0 * frontalArea / referenceArea;
		double cn = slope * flow.alphaRad();
		double cy = slope * flow.betaRad();
		double forceScale = flow.dynamicPressurePa() * referenceArea;
		double applicationX = pressureApplicationPoint(geometry);
		String region = "axisymmetric-body-incidence";
		return List.of(new ForceContribution("axisymmetric-body",
				new PhysicalOwner(PhysicalTerm.BODY_NORMAL_POTENTIAL, OwnershipMode.REPLACES,
						region, null), new MethodId(METHOD_ID),
				new Coordinate(0, cy * forceScale, cn * forceScale), new Coordinate(),
				new Coordinate(applicationX, 0, 0), region,
				List.of("BODY_NONZERO_INCIDENCE_OWNED", "COMPONENT_DERIVED_APPLICATION_POINT",
						"SIGNED_ALPHA_BETA"), 0.70, 0.20, null));
	}

	private static double pressureApplicationPoint(AeroGeometry geometry) {
		double weightedX = 0;
		double weight = 0;
		for (var component : geometry.components()) {
			if (component.axisymmetricProfile() == null) continue;
			List<GeometryStation> stations = component.axisymmetricProfile().stations();
			for (int index = 1; index < stations.size(); index++) {
				GeometryStation before = stations.get(index - 1);
				GeometryStation after = stations.get(index);
				double areaChange = Math.PI * (after.radiusM() * after.radiusM()
						- before.radiusM() * before.radiusM());
				if (areaChange <= 0) continue;
				weightedX += areaChange * 0.5 * (before.xM() + after.xM());
				weight += areaChange;
			}
		}
		return weight > 0 ? weightedX / weight : 0.5 * geometry.references().vehicleLengthM();
	}
}

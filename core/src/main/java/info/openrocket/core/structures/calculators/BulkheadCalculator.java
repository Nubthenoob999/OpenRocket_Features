package info.openrocket.core.structures.calculators;

import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.BulkheadGeometry;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class BulkheadCalculator {
	public StructuresResult calculate(BulkheadGeometry bulkhead, StructuralMaterial material, double appliedLoad,
			double requiredFactorOfSafety) {
		if (bulkhead == null || bulkhead.getOuterRadius() <= 0 || bulkhead.getThickness() <= 0) {
			return StructuresResult.insufficientData("Bulkhead", "Bulkhead capacity", "Invalid bulkhead geometry.");
		}
		if (material == null || !Double.isFinite(material.getBestAllowableStress()) || material.getBestAllowableStress() <= 0) {
			return StructuresResult.insufficientData(bulkhead.getComponentName(), "Bulkhead capacity",
					"Missing material strength.");
		}
		double allowableBendingStress = material.getBestAllowableStress() / requiredFactorOfSafety;
		double allowableShearStress = 0.6 * material.getBestAllowableStress() / requiredFactorOfSafety;

		double bendingCapacity = circularPlateBendingCapacity(bulkhead.getOuterRadius(), bulkhead.getThickness(),
				allowableBendingStress);
		double shearCapacity = circularPlateShearCapacity(bulkhead.getOuterRadius(), bulkhead.getThickness(),
				allowableShearStress);
		double governingCapacity = Math.min(bendingCapacity, shearCapacity);
		double factorOfSafety = governingCapacity / appliedLoad;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("bendingCapacity_N", bendingCapacity);
		values.put("shearCapacity_N", shearCapacity);
		values.put("governingCapacity_N", governingCapacity);
		values.put("appliedLoad_N", appliedLoad);
		values.put("requiredFoS", requiredFactorOfSafety);
		return new StructuresResult(bulkhead.getComponentName(), "Bulkhead capacity", factorOfSafety, status,
				"Minimum of bending and shear capacity", values,
				java.util.Collections.singletonList("FoS applied once through allowable stress; capacity is not divided again."));
	}

	static double circularPlateBendingCapacity(double radius, double thickness, double allowableStress) {
		return Math.PI * allowableStress * thickness * thickness;
	}

	static double circularPlateShearCapacity(double radius, double thickness, double allowableShearStress) {
		return 2.0 * Math.PI * radius * thickness * allowableShearStress;
	}
}

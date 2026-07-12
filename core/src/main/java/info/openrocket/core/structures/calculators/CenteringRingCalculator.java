package info.openrocket.core.structures.calculators;

import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.CenteringRingGeometry;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class CenteringRingCalculator {
	public StructuresResult calculate(CenteringRingGeometry ring, StructuralMaterial material, double appliedLoad,
			double requiredFactorOfSafety) {
		if (ring == null || ring.getOuterRadius() <= 0 || ring.getInnerRadius() < 0 ||
				ring.getInnerRadius() >= ring.getOuterRadius() || ring.getThickness() <= 0 || ring.getNumberOfRings() <= 0) {
			return StructuresResult.insufficientData("Centering ring", "Centering ring capacity",
					"Invalid centering ring geometry.");
		}
		if (material == null || !Double.isFinite(material.getBestAllowableStress()) || material.getBestAllowableStress() <= 0) {
			return StructuresResult.insufficientData(ring.getComponentName(), "Centering ring capacity",
					"Missing material strength.");
		}

		double allowableBendingStress = material.getBestAllowableStress() / requiredFactorOfSafety;
		double allowableShearStress = 0.6 * material.getBestAllowableStress() / requiredFactorOfSafety;
		double annularFactor = 1.0 - Math.pow(ring.getInnerRadius() / ring.getOuterRadius(), 2.0);
		double bendingCapacity = BulkheadCalculator.circularPlateBendingCapacity(ring.getOuterRadius(),
				ring.getThickness(), allowableBendingStress) * annularFactor;
		double meanRadius = 0.5 * (ring.getOuterRadius() + ring.getInnerRadius());
		double shearCapacity = BulkheadCalculator.circularPlateShearCapacity(meanRadius, ring.getThickness(),
				allowableShearStress);
		double singleRingCapacity = Math.min(bendingCapacity, shearCapacity);
		double totalCapacity = singleRingCapacity * ring.getNumberOfRings();
		double factorOfSafety = totalCapacity / appliedLoad;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("singleRingCapacity_N", singleRingCapacity);
		values.put("totalCapacity_N", totalCapacity);
		values.put("appliedLoad_N", appliedLoad);
		values.put("requiredFoS", requiredFactorOfSafety);
		return new StructuresResult(ring.getComponentName(), "Centering ring capacity", factorOfSafety, status,
				"Minimum of annular bending and shear capacity", values,
				java.util.Collections.singletonList("FoS applied once through allowable stress; capacity is not divided again."));
	}
}

package info.openrocket.core.structures.calculators;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class TubeBucklingCalculator {
	public StructuresResult calculate(TubeGeometry tube, StructuralMaterial material, double effectiveLengthFactor,
			double maxAxialLoad, double requiredFactorOfSafety) {
		if (tube == null || !tube.isComplete()) {
			return StructuresResult.insufficientData(componentName(tube), "Tube buckling", "Missing tube geometry.");
		}
		if (material == null || !material.hasRequiredStrengthAndStiffness()) {
			return StructuresResult.insufficientData(tube.getComponentName(), "Tube buckling",
					"Missing material modulus or strength.");
		}
		if (!Double.isFinite(maxAxialLoad) || maxAxialLoad <= 0 || effectiveLengthFactor <= 0) {
			return StructuresResult.insufficientData(tube.getComponentName(), "Tube buckling",
					"Missing positive axial load or effective length factor.");
		}

		double area = tube.getArea();
		double inertia = tube.getSecondMomentOfArea();
		double radiusOfGyration = Math.sqrt(inertia / area);
		double effectiveLength = effectiveLengthFactor * tube.getUnsupportedLength();
		double slenderness = effectiveLength / radiusOfGyration;
		double criticalSlenderness = Math.sqrt(2.0 * Math.PI * Math.PI * material.getYoungsModulus()
				/ material.getYieldStrength());
		double euler = Math.PI * Math.PI * material.getYoungsModulus() * inertia / (effectiveLength * effectiveLength);
		double johnson = area * material.getYieldStrength() *
				(1.0 - material.getYieldStrength() / (4.0 * Math.PI * Math.PI * material.getYoungsModulus())
						* slenderness * slenderness);
		double criticalLoad;
		String method;
		if (slenderness > criticalSlenderness) {
			criticalLoad = euler;
			method = "Euler";
		} else {
			criticalLoad = johnson;
			method = "Johnson";
		}
		double factorOfSafety = criticalLoad / maxAxialLoad;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("area_m2", area);
		values.put("secondMomentOfArea_m4", inertia);
		values.put("slenderness", slenderness);
		values.put("criticalSlenderness", criticalSlenderness);
		values.put("criticalLoad_N", criticalLoad);
		values.put("maxAxialLoad_N", maxAxialLoad);
		values.put("requiredFoS", requiredFactorOfSafety);
		return new StructuresResult(tube.getComponentName(), "Tube buckling", factorOfSafety, status, method,
				values, Arrays.asList("Axial load uses conservative axial load estimate."));
	}

	private static String componentName(TubeGeometry tube) {
		return tube == null ? "Tube" : tube.getComponentName();
	}
}

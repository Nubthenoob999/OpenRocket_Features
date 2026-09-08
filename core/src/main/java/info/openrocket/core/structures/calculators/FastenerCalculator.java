package info.openrocket.core.structures.calculators;

import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class FastenerCalculator {
	public StructuresResult calculate(String componentName, double rodMinorDiameter, int numberOfRods,
			double fastenerDiameter, int numberOfShearFasteners, int numberOfShearPlanes, double recoveryLoad,
			double shearLoad, StructuralMaterial material, double requiredFactorOfSafety) {
		if (material == null || !Double.isFinite(material.getTensileAllowable())
				|| material.getTensileAllowable() <= 0) {
			return StructuresResult.insufficientData(componentName, "Fastener capacity",
					"Missing fastener material tensile limit.");
		}
		if (rodMinorDiameter <= 0 || numberOfRods <= 0 || fastenerDiameter <= 0 ||
				numberOfShearFasteners <= 0 || numberOfShearPlanes <= 0) {
			return StructuresResult.insufficientData(componentName, "Fastener capacity", "Invalid fastener geometry/count.");
		}

		double tensileArea = Math.PI * Math.pow(rodMinorDiameter / 2.0, 2.0) * numberOfRods;
		double tensileStress = recoveryLoad / tensileArea;
		double tensileFoS = material.getTensileAllowable() / tensileStress;
		double shearArea = Math.PI * Math.pow(fastenerDiameter / 2.0, 2.0) * numberOfShearFasteners * numberOfShearPlanes;
		double shearStress = shearLoad / shearArea;
		double shearFoS = 0.6 * material.getTensileAllowable() / shearStress;
		double governingFoS = Math.min(tensileFoS, shearFoS);
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(governingFoS, requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("tensileStress_Pa", tensileStress);
		values.put("tensileFoS", tensileFoS);
		values.put("shearStress_Pa", shearStress);
		values.put("shearFoS", shearFoS);
		values.put("requiredFoS", requiredFactorOfSafety);
		return new StructuresResult(componentName, "Fastener capacity", governingFoS, status,
				"Minimum of tensile and shear FoS", values, java.util.Collections.emptyList());
	}
}

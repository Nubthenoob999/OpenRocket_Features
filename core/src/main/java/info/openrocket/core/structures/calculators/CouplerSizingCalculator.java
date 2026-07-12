package info.openrocket.core.structures.calculators;

import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;

public final class CouplerSizingCalculator {
	public StructuresResult calculate(String componentName, double airframeOuterDiameter, double airframeInnerDiameter,
			double airframeModulus, double couplerModulus, double targetStiffnessRatio) {
		if (airframeOuterDiameter <= 0 || airframeInnerDiameter <= 0 ||
				airframeInnerDiameter >= airframeOuterDiameter || airframeModulus <= 0 || couplerModulus <= 0 ||
				targetStiffnessRatio <= 0) {
			return StructuresResult.insufficientData(componentName, "Coupler sizing", "Invalid coupler sizing input.");
		}
		double targetEI = airframeModulus * secondMoment(airframeOuterDiameter, airframeInnerDiameter) * targetStiffnessRatio;
		double couplerOuterDiameter = airframeInnerDiameter;
		double low = 0.0;
		double high = couplerOuterDiameter - 1.0e-9;
		for (int i = 0; i < 100; i++) {
			double mid = 0.5 * (low + high);
			double ei = couplerModulus * secondMoment(couplerOuterDiameter, mid);
			if (ei >= targetEI) {
				low = mid;
			} else {
				high = mid;
			}
		}
		double couplerInnerDiameter = low;
		double requiredWallThickness = 0.5 * (couplerOuterDiameter - couplerInnerDiameter);
		double stiffnessRatio = couplerModulus * secondMoment(couplerOuterDiameter, couplerInnerDiameter) /
				(airframeModulus * secondMoment(airframeOuterDiameter, airframeInnerDiameter));
		StructuresStatus status = stiffnessRatio >= targetStiffnessRatio ? StructuresStatus.PASS : StructuresStatus.FAIL;

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("requiredWallThickness_m", requiredWallThickness);
		values.put("couplerOD_m", couplerOuterDiameter);
		values.put("couplerID_m", couplerInnerDiameter);
		values.put("stiffnessRatio", stiffnessRatio);
		values.put("targetStiffnessRatio", targetStiffnessRatio);
		return new StructuresResult(componentName, "Coupler sizing", stiffnessRatio / targetStiffnessRatio, status,
				"Equal bending stiffness target", values, java.util.Collections.emptyList());
	}

	static double secondMoment(double outerDiameter, double innerDiameter) {
		return Math.PI / 64.0 * (Math.pow(outerDiameter, 4.0) - Math.pow(innerDiameter, 4.0));
	}
}

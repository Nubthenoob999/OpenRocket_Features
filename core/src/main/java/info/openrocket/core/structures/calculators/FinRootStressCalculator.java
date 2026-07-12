package info.openrocket.core.structures.calculators;

import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.loads.NormalForceLoadModel;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class FinRootStressCalculator {
	public StructuresResult calculate(FinGeometryStructural fin, StructuralMaterial material, FlightLoadSeries loadSeries,
			double bodyRadius, double requiredFactorOfSafety) {
		if (fin == null || !fin.isComplete()) {
			return StructuresResult.insufficientData("Fin set", "Fin root stress", "Missing fin geometry or thickness.");
		}
		if (material == null || !Double.isFinite(material.getBestAllowableStress()) || material.getBestAllowableStress() <= 0) {
			return StructuresResult.insufficientData(fin.getComponentName(), "Fin root stress", "Missing material strength.");
		}
		if (loadSeries == null || loadSeries.isEmpty()) {
			return StructuresResult.insufficientData(fin.getComponentName(), "Fin root stress", "No simulation loads.");
		}

		FlightLoadCase loadCase = loadSeries.getMaxDynamicPressureCase();
		double alpha = loadCase.getAngleOfAttack();
		if (!Double.isFinite(alpha)) {
			alpha = 0.0;
		}
		double cnAlpha = NormalForceLoadModel.finCnAlpha(fin, bodyRadius);
		double normalForceTotal = loadCase.getDynamicPressure() * Math.PI * bodyRadius * bodyRadius * alpha * cnAlpha;
		double normalForcePerFin = normalForceTotal / fin.getFinCount();
		double yMac = 0.5 * fin.getSemiSpan();
		double rootMoment = Math.abs(normalForcePerFin * yMac);
		double sectionModulus = fin.getRootChord() * fin.getThickness() * fin.getThickness() / 6.0;
		double stress = rootMoment / sectionModulus;
		double factorOfSafety = material.getBestAllowableStress() / stress;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("normalForceTotal_N", normalForceTotal);
		values.put("normalForcePerFin_N", normalForcePerFin);
		values.put("rootMoment_Nm", rootMoment);
		values.put("rootStress_Pa", stress);
		values.put("requiredFoS", requiredFactorOfSafety);
		return new StructuresResult(fin.getComponentName(), "Fin root stress", factorOfSafety, status,
				"Max dynamic pressure case", values, java.util.Collections.emptyList());
	}
}

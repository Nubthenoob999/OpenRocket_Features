package info.openrocket.core.structures.calculators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.loads.AxialLoadModel;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.loads.NormalForceLoadModel;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class TubeStressCalculator {
	public StructuresResult calculate(TubeGeometry tube, StructuralMaterial material, FlightLoadSeries loadSeries,
			NoseConeGeometryStructural nose, FinGeometryStructural fin, double requiredFactorOfSafety) {
		if (tube == null || !tube.isComplete()) {
			return StructuresResult.insufficientData("Tube", "Tube stress", "Missing tube geometry.");
		}
		if (material == null || !material.hasRequiredStrengthAndStiffness()) {
			return StructuresResult.insufficientData(tube.getComponentName(), "Tube stress",
					"Missing material modulus or strength.");
		}
		if (loadSeries == null || loadSeries.isEmpty()) {
			return StructuresResult.insufficientData(tube.getComponentName(), "Tube stress", "No simulation loads.");
		}

		double worstStress = Double.NaN;
		double worstAxial = Double.NaN;
		double worstBending = Double.NaN;
		double worstMoment = Double.NaN;
		double worstTime = Double.NaN;
		for (FlightLoadCase loadCase : loadSeries.getCases()) {
			if (!Double.isFinite(loadCase.getDynamicPressure())) {
				continue;
			}
			double axialLoad = AxialLoadModel.conservativeAxialLoad(loadCase);
			double axialStress = axialLoad / tube.getArea();
			double moment = NormalForceLoadModel.bendingMomentAtTube(tube, nose, fin, loadCase);
			double bendingStress = moment * tube.getOuterRadius() / tube.getSecondMomentOfArea();
			double total = axialStress + bendingStress;
			if (!Double.isFinite(worstStress) || total > worstStress) {
				worstStress = total;
				worstAxial = axialStress;
				worstBending = bendingStress;
				worstMoment = moment;
				worstTime = loadCase.getTime();
			}
		}
		if (!Double.isFinite(worstStress) || worstStress <= 0) {
			return StructuresResult.insufficientData(tube.getComponentName(), "Tube stress",
					"No finite stress case could be computed from the selected simulation.");
		}

		double allowable = material.getBestAllowableStress();
		double factorOfSafety = allowable / worstStress;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("allowableStress_Pa", allowable);
		values.put("maxStress_Pa", worstStress);
		values.put("axialStress_Pa", worstAxial);
		values.put("bendingStress_Pa", worstBending);
		values.put("bendingMoment_Nm", worstMoment);
		values.put("governingTime_s", worstTime);
		values.put("requiredFoS", requiredFactorOfSafety);

		List<String> warnings = new ArrayList<>();
		warnings.add("Axial load uses conservative axial load estimate.");
		warnings.add("Hole-reduced stress skipped: no hole data supplied.");
		warnings.add("Anisotropic correction skipped: insufficient material data.");
		return new StructuresResult(tube.getComponentName(), "Tube stress", factorOfSafety, status,
				"Max combined axial and bending stress", values, warnings);
	}
}

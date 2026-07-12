package info.openrocket.core.structures.calculators;

import java.util.LinkedHashMap;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class FinFlutterCalculator {
	private static final double SEA_LEVEL_PRESSURE = 101325.0;
	private static final double SEA_LEVEL_SPEED_OF_SOUND = 340.294;

	public StructuresResult calculate(FinGeometryStructural fin, StructuralMaterial material, FlightLoadSeries loadSeries,
			double requiredFactorOfSafety) {
		if (fin == null || !fin.isComplete()) {
			return StructuresResult.insufficientData("Fin set", "Fin flutter", "Missing fin geometry or thickness.");
		}
		if (material == null || !Double.isFinite(material.getShearModulus()) || material.getShearModulus() <= 0) {
			return StructuresResult.insufficientData(fin.getComponentName(), "Fin flutter", "Missing material shear modulus.");
		}
		if (loadSeries == null || loadSeries.isEmpty()) {
			return StructuresResult.insufficientData(fin.getComponentName(), "Fin flutter", "No simulation loads.");
		}

		FlightLoadCase maxVelocity = loadSeries.getMaxVelocityCase();
		double pressureRatio = finiteOr(maxVelocity.getAirPressure(), SEA_LEVEL_PRESSURE) / SEA_LEVEL_PRESSURE;
		if (pressureRatio <= 0) {
			pressureRatio = 1.0;
		}
		double speedOfSound = finiteOr(maxVelocity.getSpeedOfSound(), SEA_LEVEL_SPEED_OF_SOUND);
		double flutterVelocity = flutterVelocity(fin, material.getShearModulus(), speedOfSound, pressureRatio,
				fin.getThickness());
		double maxFlightVelocity = Math.abs(maxVelocity.getVelocity());
		double factorOfSafety = flutterVelocity / maxFlightVelocity;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);
		double minimumThickness = minimumThicknessForTarget(fin, material.getShearModulus(), speedOfSound, pressureRatio,
				maxFlightVelocity * requiredFactorOfSafety);

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("flutterVelocity_mps", flutterVelocity);
		values.put("maxFlightVelocity_mps", maxFlightVelocity);
		values.put("minimumThicknessForTargetFoS_m", minimumThickness);
		values.put("pressureRatio", pressureRatio);
		values.put("requiredFoS", requiredFactorOfSafety);
		return new StructuresResult(fin.getComponentName(), "Fin flutter", factorOfSafety, status,
				"Max velocity case", values, java.util.Collections.singletonList(
						"Flutter equation uses SI-consistent NACA-style pressure ratio form."));
	}

	static double flutterVelocity(FinGeometryStructural fin, double shearModulus, double speedOfSound,
			double pressureRatio, double thickness) {
		double aspectRatio = fin.getSemiSpan() * fin.getSemiSpan() / fin.getPlanformArea();
		double taperRatio = fin.getTipChord() / fin.getRootChord();
		double thicknessRatio = thickness / fin.getRootChord();
		double denominator = 1.337 * pressureRatio * Math.pow(aspectRatio, 3.0) / (taperRatio + 1.0);
		return speedOfSound * Math.sqrt(shearModulus / denominator) * Math.pow(thicknessRatio, 1.5);
	}

	private static double minimumThicknessForTarget(FinGeometryStructural fin, double shearModulus, double speedOfSound,
			double pressureRatio, double targetVelocity) {
		if (targetVelocity <= 0) {
			return Double.NaN;
		}
		double low = 1.0e-5;
		double high = Math.max(fin.getRootChord(), fin.getThickness() * 4.0);
		for (int i = 0; i < 80; i++) {
			double mid = 0.5 * (low + high);
			double velocity = flutterVelocity(fin, shearModulus, speedOfSound, pressureRatio, mid);
			if (velocity >= targetVelocity) {
				high = mid;
			} else {
				low = mid;
			}
		}
		return high;
	}

	private static double finiteOr(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}
}

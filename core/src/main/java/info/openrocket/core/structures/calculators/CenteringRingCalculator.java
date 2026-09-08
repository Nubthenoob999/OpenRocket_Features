package info.openrocket.core.structures.calculators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.calculators.BulkheadCalculator.PlateCapacity;
import info.openrocket.core.structures.geometry.CenteringRingGeometry;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class CenteringRingCalculator {
	private static final double DEFAULT_POISSON_RATIO = 0.30;

	public StructuresResult calculate(CenteringRingGeometry ring, StructuralMaterial material, double appliedLoad,
			double requiredFactorOfSafety) {
		if (ring == null || ring.getOuterRadius() <= 0 || ring.getInnerRadius() <= 0
				|| ring.getInnerRadius() >= ring.getOuterRadius() || ring.getThickness() <= 0
				|| ring.getNumberOfRings() <= 0) {
			return StructuresResult.insufficientData("Centering ring", "Centering ring capacity",
					"Invalid centering ring geometry.");
		}
		if (material == null || !positive(material.getCompressiveStrength())) {
			return StructuresResult.insufficientData(ring.getComponentName(), "Centering ring capacity",
					"Missing material compressive strength.");
		}
		double shearStrength = shearStrength(material);
		if (!positive(shearStrength)) {
			return StructuresResult.insufficientData(ring.getComponentName(), "Centering ring capacity",
					"Missing material shear strength estimate.");
		}
		if (!positive(appliedLoad) || !positive(requiredFactorOfSafety)) {
			return StructuresResult.insufficientData(ring.getComponentName(), "Centering ring capacity",
					"Applied load and required factor of safety must be positive.");
		}

		double poissonRatio = poissonRatio(material);
		PlateCapacity perRing = BulkheadCalculator.lineLoadPlateCapacity(ring.getInnerRadius(),
				ring.getOuterRadius(), ring.getOuterRadius(), ring.getThickness(), poissonRatio,
				material.getCompressiveStrength(), shearStrength);
		if (!perRing.isFinite()) {
			return StructuresResult.insufficientData(ring.getComponentName(), "Centering ring capacity",
					"Roark annular-plate coefficients are invalid for this geometry.");
		}

		double totalBendingCapacity = perRing.bendingCapacity * ring.getNumberOfRings();
		double totalShearCapacity = perRing.shearCapacity * ring.getNumberOfRings();
		double totalCapacity = perRing.governingCapacity * ring.getNumberOfRings();
		double factorOfSafety = totalCapacity / appliedLoad;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);
		Map<String, Double> values = perRing.values();
		values.put("singleRingCapacity_N", perRing.governingCapacity);
		values.put("totalBendingCapacity_N", totalBendingCapacity);
		values.put("totalShearCapacity_N", totalShearCapacity);
		values.put("totalCapacity_N", totalCapacity);
		values.put("designTotalCapacity_N", totalCapacity / requiredFactorOfSafety);
		values.put("appliedLoad_N", appliedLoad);
		values.put("requiredFoS", requiredFactorOfSafety);

		List<String> warnings = new ArrayList<>();
		warnings.add("Roark annular-plate line-load model with the motor-tube radius as the load radius.");
		if (material.getPoissonRatio() == null) {
			warnings.add("Poisson ratio is unavailable; 0.30 is used for the Roark coefficients.");
		}
		if (!positive(material.getShearStrength())) {
			warnings.add("Shear strength is unavailable; it is estimated as 0.6 x tensile strength.");
		}
		return new StructuresResult(ring.getComponentName(), "Centering ring capacity", factorOfSafety, status,
				"Minimum of Roark bending and transverse-shear capacity", values, warnings);
	}

	private static double poissonRatio(StructuralMaterial material) {
		Double value = material.getPoissonRatio();
		return value != null && Double.isFinite(value) ? value : DEFAULT_POISSON_RATIO;
	}

	private static double shearStrength(StructuralMaterial material) {
		if (positive(material.getShearStrength())) {
			return material.getShearStrength();
		}
		double tensile = material.getTensileAllowable();
		return positive(tensile) ? 0.6 * tensile : Double.NaN;
	}

	private static boolean positive(double value) {
		return Double.isFinite(value) && value > 0;
	}
}

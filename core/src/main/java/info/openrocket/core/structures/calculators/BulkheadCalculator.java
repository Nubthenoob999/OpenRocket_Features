package info.openrocket.core.structures.calculators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.BulkheadGeometry;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class BulkheadCalculator {
	private static final double DEFAULT_LOAD_RADIUS = 0.003175; // 0.125 in, matching the workbook fixture.
	private static final double DEFAULT_POISSON_RATIO = 0.30;

	public StructuresResult calculate(BulkheadGeometry bulkhead, StructuralMaterial material, double appliedLoad,
			double requiredFactorOfSafety) {
		if (bulkhead == null || bulkhead.getOuterRadius() <= 0 || bulkhead.getThickness() <= 0) {
			return StructuresResult.insufficientData("Bulkhead", "Bulkhead capacity", "Invalid bulkhead geometry.");
		}
		if (material == null || !positive(material.getCompressiveStrength())) {
			return StructuresResult.insufficientData(bulkhead.getComponentName(), "Bulkhead capacity",
					"Missing material compressive strength.");
		}
		double shearStrength = shearStrength(material);
		if (!positive(shearStrength)) {
			return StructuresResult.insufficientData(bulkhead.getComponentName(), "Bulkhead capacity",
					"Missing material shear strength estimate.");
		}
		if (!positive(appliedLoad) || !positive(requiredFactorOfSafety)) {
			return StructuresResult.insufficientData(bulkhead.getComponentName(), "Bulkhead capacity",
					"Applied load and required factor of safety must be positive.");
		}

		double poissonRatio = poissonRatio(material);
		double loadRadius = Math.min(DEFAULT_LOAD_RADIUS, 0.5 * bulkhead.getOuterRadius());
		PlateCapacity capacity = lineLoadPlateCapacity(loadRadius, bulkhead.getOuterRadius(), loadRadius,
				bulkhead.getThickness(), poissonRatio, material.getCompressiveStrength(), shearStrength);
		if (!capacity.isFinite()) {
			return StructuresResult.insufficientData(bulkhead.getComponentName(), "Bulkhead capacity",
					"Roark annular-plate coefficients are invalid for this geometry.");
		}

		double factorOfSafety = capacity.governingCapacity / appliedLoad;
		StructuresStatus status = StructuresStatusDecider.forFactorOfSafety(factorOfSafety, requiredFactorOfSafety);
		Map<String, Double> values = capacity.values();
		values.put("designBendingCapacity_N", capacity.bendingCapacity / requiredFactorOfSafety);
		values.put("designShearCapacity_N", capacity.shearCapacity / requiredFactorOfSafety);
		values.put("designGoverningCapacity_N", capacity.governingCapacity / requiredFactorOfSafety);
		values.put("appliedLoad_N", appliedLoad);
		values.put("requiredFoS", requiredFactorOfSafety);

		List<String> warnings = new ArrayList<>();
		warnings.add("Roark annular-plate line-load model; a 0.125 in central load/fixture radius is assumed.");
		if (material.getPoissonRatio() == null) {
			warnings.add("Poisson ratio is unavailable; 0.30 is used for the Roark coefficients.");
		}
		if (!positive(material.getShearStrength())) {
			warnings.add("Shear strength is unavailable; it is estimated as 0.6 x tensile strength.");
		}
		return new StructuresResult(bulkhead.getComponentName(), "Bulkhead capacity", factorOfSafety, status,
				"Minimum of Roark bending and transverse-shear capacity", values, warnings);
	}

	static PlateCapacity lineLoadPlateCapacity(double loadRadius, double outerRadius, double supportRadius,
			double thickness, double poissonRatio, double compressiveStrength, double shearStrength) {
		if (!positive(loadRadius) || !positive(outerRadius) || loadRadius >= outerRadius
				|| !positive(supportRadius) || supportRadius > outerRadius || !positive(thickness)
				|| !Double.isFinite(poissonRatio) || poissonRatio <= 0 || poissonRatio >= 0.5
				|| !positive(compressiveStrength) || !positive(shearStrength)) {
			return PlateCapacity.invalid();
		}

		double loadRatio = loadRadius / outerRadius;
		double supportRatio = supportRadius / outerRadius;
		double l6 = loadRatio / 4.0 * (loadRatio * loadRatio - 1.0
				+ 2.0 * Math.log(1.0 / loadRatio));
		double l9 = loadRatio * ((1.0 + poissonRatio) / 2.0 * Math.log(1.0 / loadRatio)
				+ (1.0 - poissonRatio) / 4.0 * (1.0 - loadRatio * loadRatio));
		double c4 = 0.5 * ((1.0 + poissonRatio) * supportRatio
				+ (1.0 - poissonRatio) / supportRatio);
		double c7 = 0.5 * (1.0 - poissonRatio * poissonRatio)
				* (1.0 / supportRatio - supportRatio);
		double alphaM = -(l9 - c7 * l6 / c4);
		double alphaQ = -loadRatio;
		double bendingCapacity = thickness * thickness * compressiveStrength * 2.0 * Math.PI * loadRadius
				/ (6.0 * outerRadius * Math.abs(alphaM));
		double shearCapacity = thickness * shearStrength * 2.0 * Math.PI * loadRadius / Math.abs(alphaQ);
		return new PlateCapacity(l6, l9, c4, c7, alphaM, alphaQ, bendingCapacity, shearCapacity);
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

	static final class PlateCapacity {
		final double l6;
		final double l9;
		final double c4;
		final double c7;
		final double alphaM;
		final double alphaQ;
		final double bendingCapacity;
		final double shearCapacity;
		final double governingCapacity;

		private PlateCapacity(double l6, double l9, double c4, double c7, double alphaM, double alphaQ,
				double bendingCapacity, double shearCapacity) {
			this.l6 = l6;
			this.l9 = l9;
			this.c4 = c4;
			this.c7 = c7;
			this.alphaM = alphaM;
			this.alphaQ = alphaQ;
			this.bendingCapacity = bendingCapacity;
			this.shearCapacity = shearCapacity;
			this.governingCapacity = Math.min(bendingCapacity, shearCapacity);
		}

		private static PlateCapacity invalid() {
			return new PlateCapacity(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					Double.NaN, Double.NaN);
		}

		boolean isFinite() {
			return positive(governingCapacity);
		}

		Map<String, Double> values() {
			Map<String, Double> values = new LinkedHashMap<>();
			values.put("L6", l6);
			values.put("L9", l9);
			values.put("C4", c4);
			values.put("C7", c7);
			values.put("alphaM", alphaM);
			values.put("alphaQ", alphaQ);
			values.put("bendingCapacity_N", bendingCapacity);
			values.put("shearCapacity_N", shearCapacity);
			values.put("governingCapacity_N", governingCapacity);
			return values;
		}
	}
}

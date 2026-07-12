package info.openrocket.core.structures.calculators;

import info.openrocket.core.structures.StructuresStatus;

final class StructuresStatusDecider {
	private StructuresStatusDecider() {
	}

	static StructuresStatus forFactorOfSafety(double factorOfSafety, double requiredFactorOfSafety) {
		if (!Double.isFinite(factorOfSafety) || !Double.isFinite(requiredFactorOfSafety) || requiredFactorOfSafety <= 0) {
			return StructuresStatus.INSUFFICIENT_DATA;
		}
		if (factorOfSafety >= requiredFactorOfSafety) {
			return StructuresStatus.PASS;
		}
		if (factorOfSafety >= 0.9 * requiredFactorOfSafety) {
			return StructuresStatus.WARNING;
		}
		return StructuresStatus.FAIL;
	}
}

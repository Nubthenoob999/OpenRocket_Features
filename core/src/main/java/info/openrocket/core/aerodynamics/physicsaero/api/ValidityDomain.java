package info.openrocket.core.aerodynamics.physicsaero.api;

public record ValidityDomain(double minMach, double maxMach, double minReynolds, double maxReynolds,
		double minIncidenceRad, double maxIncidenceRad, String geometry, String thermal) {
	public ValidityDomain {
		if (!Double.isFinite(minMach) || !Double.isFinite(maxMach) || minMach > maxMach || minMach < 0) {
			throw new IllegalArgumentException("invalid Mach range");
		}
		if (minReynolds < 0 || minReynolds > maxReynolds) throw new IllegalArgumentException("invalid Reynolds range");
		if (minIncidenceRad > maxIncidenceRad) throw new IllegalArgumentException("invalid incidence range");
	}

	public boolean contains(double mach, double reynolds, double incidenceRad) {
		return mach >= minMach && mach <= maxMach && reynolds >= minReynolds && reynolds <= maxReynolds
				&& incidenceRad >= minIncidenceRad && incidenceRad <= maxIncidenceRad;
	}
}

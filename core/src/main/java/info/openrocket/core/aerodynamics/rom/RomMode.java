package info.openrocket.core.aerodynamics.rom;

/**
 * Phase I ROM operating presets.
 */
public enum RomMode {
	STANDARD(12, 3, 1.0),
	CONSERVATIVE(8, 2, 0.85),
	DIAGNOSTIC(18, 4, 1.1);

	private final int bodySeedCount;
	private final int finSeedCount;
	private final double normalForceGain;

	RomMode(int bodySeedCount, int finSeedCount, double normalForceGain) {
		this.bodySeedCount = bodySeedCount;
		this.finSeedCount = finSeedCount;
		this.normalForceGain = normalForceGain;
	}

	public int getBodySeedCount() {
		return bodySeedCount;
	}

	public int getFinSeedCount() {
		return finSeedCount;
	}

	public double getNormalForceGain() {
		return normalForceGain;
	}
}

package info.openrocket.core.aerodynamics.rom;

import java.util.Objects;

public class RomSettings implements Cloneable {
	private boolean enabled = false;
	private RomMode mode = RomMode.STANDARD;
	private RomFallbackMode fallbackMode = RomFallbackMode.BLEND;
	private boolean diagnosticsEnabled = true;
	private int bodyMeridianSeedCount = RomMode.STANDARD.getBodySeedCount();
	private int finSurfaceSeedCount = RomMode.STANDARD.getFinSeedCount();
	private double transonicBandHalfWidth = 0.20;
	private double highAngleDeg = 12.0;
	private double maxTrustedSeparationFraction = 0.25;
	private double prestepMach = 0.80;
	private double prestepAngleOfAttackDeg = 5.0;
	private double prestepThetaDeg = 0.0;
	private double prestepPlumeState = 0.0;

	public static RomSettings defaults() {
		return new RomSettings();
	}

	public RomSettings copy() {
		try {
			return (RomSettings) clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException("Clone failure", e);
		}
	}

	@Override
	public RomSettings clone() throws CloneNotSupportedException {
		return (RomSettings) super.clone();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public RomMode getMode() {
		return mode;
	}

	public void setMode(RomMode mode) {
		RomMode normalized = mode != null ? mode : RomMode.STANDARD;
		this.mode = normalized;
		if (bodyMeridianSeedCount <= 0) {
			bodyMeridianSeedCount = normalized.getBodySeedCount();
		}
		if (finSurfaceSeedCount <= 0) {
			finSurfaceSeedCount = normalized.getFinSeedCount();
		}
	}

	public RomFallbackMode getFallbackMode() {
		return fallbackMode;
	}

	public void setFallbackMode(RomFallbackMode fallbackMode) {
		this.fallbackMode = fallbackMode != null ? fallbackMode : RomFallbackMode.BLEND;
	}

	public boolean isDiagnosticsEnabled() {
		return diagnosticsEnabled;
	}

	public void setDiagnosticsEnabled(boolean diagnosticsEnabled) {
		this.diagnosticsEnabled = diagnosticsEnabled;
	}

	public int getBodyMeridianSeedCount() {
		return Math.max(2, bodyMeridianSeedCount);
	}

	public void setBodyMeridianSeedCount(int bodyMeridianSeedCount) {
		this.bodyMeridianSeedCount = Math.max(2, bodyMeridianSeedCount);
	}

	public int getFinSurfaceSeedCount() {
		return Math.max(1, finSurfaceSeedCount);
	}

	public void setFinSurfaceSeedCount(int finSurfaceSeedCount) {
		this.finSurfaceSeedCount = Math.max(1, finSurfaceSeedCount);
	}

	public double getTransonicBandHalfWidth() {
		return transonicBandHalfWidth;
	}

	public void setTransonicBandHalfWidth(double transonicBandHalfWidth) {
		this.transonicBandHalfWidth = Math.max(0.05, transonicBandHalfWidth);
	}

	public double getHighAngleDeg() {
		return highAngleDeg;
	}

	public void setHighAngleDeg(double highAngleDeg) {
		this.highAngleDeg = Math.max(1.0, highAngleDeg);
	}

	public double getMaxTrustedSeparationFraction() {
		return maxTrustedSeparationFraction;
	}

	public void setMaxTrustedSeparationFraction(double maxTrustedSeparationFraction) {
		this.maxTrustedSeparationFraction = Math.max(0.01, Math.min(1.0, maxTrustedSeparationFraction));
	}

	public double getPrestepMach() {
		return clamp(prestepMach, 0.0, 8.0);
	}

	public void setPrestepMach(double prestepMach) {
		this.prestepMach = clamp(prestepMach, 0.0, 8.0);
	}

	public double getPrestepAngleOfAttackDeg() {
		return clamp(prestepAngleOfAttackDeg, -30.0, 30.0);
	}

	public void setPrestepAngleOfAttackDeg(double prestepAngleOfAttackDeg) {
		this.prestepAngleOfAttackDeg = clamp(prestepAngleOfAttackDeg, -30.0, 30.0);
	}

	public double getPrestepThetaDeg() {
		return clamp(prestepThetaDeg, -180.0, 180.0);
	}

	public void setPrestepThetaDeg(double prestepThetaDeg) {
		this.prestepThetaDeg = clamp(prestepThetaDeg, -180.0, 180.0);
	}

	public double getPrestepPlumeState() {
		return clamp(prestepPlumeState, 0.0, 1.0);
	}

	public void setPrestepPlumeState(double prestepPlumeState) {
		this.prestepPlumeState = clamp(prestepPlumeState, 0.0, 1.0);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof RomSettings that)) {
			return false;
		}
		return enabled == that.enabled
				&& diagnosticsEnabled == that.diagnosticsEnabled
				&& bodyMeridianSeedCount == that.bodyMeridianSeedCount
				&& finSurfaceSeedCount == that.finSurfaceSeedCount
				&& Double.compare(transonicBandHalfWidth, that.transonicBandHalfWidth) == 0
				&& Double.compare(highAngleDeg, that.highAngleDeg) == 0
				&& Double.compare(maxTrustedSeparationFraction, that.maxTrustedSeparationFraction) == 0
				&& Double.compare(prestepMach, that.prestepMach) == 0
				&& Double.compare(prestepAngleOfAttackDeg, that.prestepAngleOfAttackDeg) == 0
				&& Double.compare(prestepThetaDeg, that.prestepThetaDeg) == 0
				&& Double.compare(prestepPlumeState, that.prestepPlumeState) == 0
				&& mode == that.mode
				&& fallbackMode == that.fallbackMode;
	}

	@Override
	public int hashCode() {
		return Objects.hash(enabled, mode, fallbackMode, diagnosticsEnabled, bodyMeridianSeedCount,
				finSurfaceSeedCount, transonicBandHalfWidth, highAngleDeg, maxTrustedSeparationFraction,
				prestepMach, prestepAngleOfAttackDeg, prestepThetaDeg, prestepPlumeState);
	}
}

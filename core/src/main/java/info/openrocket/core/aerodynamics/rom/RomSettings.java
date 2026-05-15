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
	private double previewMachMin = 0.20;
	private double previewMachMax = 1.40;
	private double previewMachStep = 0.10;
	private double previewAoADegMin = 0.0;
	private double previewAoADegMax = 12.0;
	private double previewAoADegStep = 2.0;
	private double previewThetaDeg = 0.0;
	private double previewPlumeState = 0.0;
	private int previewMaxRows = 250;

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

	public double getPreviewMachMin() {
		return clamp(previewMachMin, 0.0, 8.0);
	}

	public void setPreviewMachMin(double previewMachMin) {
		this.previewMachMin = clamp(previewMachMin, 0.0, 8.0);
	}

	public RomSettings withPreviewMachMin(double v) {
		RomSettings c = copy();
		c.setPreviewMachMin(v);
		return c;
	}

	public double getPreviewMachMax() {
		return clamp(previewMachMax, 0.0, 8.0);
	}

	public void setPreviewMachMax(double previewMachMax) {
		this.previewMachMax = clamp(previewMachMax, 0.0, 8.0);
	}

	public RomSettings withPreviewMachMax(double v) {
		RomSettings c = copy();
		c.setPreviewMachMax(v);
		return c;
	}

	public double getPreviewMachStep() {
		return Math.max(0.001, previewMachStep);
	}

	public void setPreviewMachStep(double previewMachStep) {
		this.previewMachStep = Math.max(0.001, previewMachStep);
	}

	public RomSettings withPreviewMachStep(double v) {
		RomSettings c = copy();
		c.setPreviewMachStep(v);
		return c;
	}

	public double getPreviewAoADegMin() {
		return clamp(previewAoADegMin, 0.0, 45.0);
	}

	public void setPreviewAoADegMin(double previewAoADegMin) {
		this.previewAoADegMin = clamp(previewAoADegMin, 0.0, 45.0);
	}

	public RomSettings withPreviewAoADegMin(double v) {
		RomSettings c = copy();
		c.setPreviewAoADegMin(v);
		return c;
	}

	public double getPreviewAoADegMax() {
		return clamp(previewAoADegMax, 0.0, 45.0);
	}

	public void setPreviewAoADegMax(double previewAoADegMax) {
		this.previewAoADegMax = clamp(previewAoADegMax, 0.0, 45.0);
	}

	public RomSettings withPreviewAoADegMax(double v) {
		RomSettings c = copy();
		c.setPreviewAoADegMax(v);
		return c;
	}

	public double getPreviewAoADegStep() {
		return Math.max(0.001, previewAoADegStep);
	}

	public void setPreviewAoADegStep(double previewAoADegStep) {
		this.previewAoADegStep = Math.max(0.001, previewAoADegStep);
	}

	public RomSettings withPreviewAoADegStep(double v) {
		RomSettings c = copy();
		c.setPreviewAoADegStep(v);
		return c;
	}

	public double getPreviewThetaDeg() {
		return clamp(previewThetaDeg, -180.0, 180.0);
	}

	public void setPreviewThetaDeg(double previewThetaDeg) {
		this.previewThetaDeg = clamp(previewThetaDeg, -180.0, 180.0);
	}

	public RomSettings withPreviewThetaDeg(double v) {
		RomSettings c = copy();
		c.setPreviewThetaDeg(v);
		return c;
	}

	public double getPreviewPlumeState() {
		return clamp(previewPlumeState, 0.0, 1.0);
	}

	public void setPreviewPlumeState(double previewPlumeState) {
		this.previewPlumeState = clamp(previewPlumeState, 0.0, 1.0);
	}

	public RomSettings withPreviewPlumeState(double v) {
		RomSettings c = copy();
		c.setPreviewPlumeState(v);
		return c;
	}

	public int getPreviewMaxRows() {
		return Math.max(1, previewMaxRows);
	}

	public void setPreviewMaxRows(int previewMaxRows) {
		this.previewMaxRows = Math.max(1, previewMaxRows);
	}

	public RomSettings withPreviewMaxRows(int v) {
		RomSettings c = copy();
		c.setPreviewMaxRows(v);
		return c;
	}

	/**
	 * Normalize the preview sweep range fields: if min &gt; max, swap them.
	 * Returns a copy if any swap occurs; otherwise returns this.
	 */
	public RomSettings normalizedPreviewRanges() {
		RomSettings c = copy();
		if (c.previewMachMin > c.previewMachMax) {
			double t = c.previewMachMin;
			c.previewMachMin = c.previewMachMax;
			c.previewMachMax = t;
		}
		if (c.previewAoADegMin > c.previewAoADegMax) {
			double t = c.previewAoADegMin;
			c.previewAoADegMin = c.previewAoADegMax;
			c.previewAoADegMax = t;
		}
		return c;
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
				&& Double.compare(previewMachMin, that.previewMachMin) == 0
				&& Double.compare(previewMachMax, that.previewMachMax) == 0
				&& Double.compare(previewMachStep, that.previewMachStep) == 0
				&& Double.compare(previewAoADegMin, that.previewAoADegMin) == 0
				&& Double.compare(previewAoADegMax, that.previewAoADegMax) == 0
				&& Double.compare(previewAoADegStep, that.previewAoADegStep) == 0
				&& Double.compare(previewThetaDeg, that.previewThetaDeg) == 0
				&& Double.compare(previewPlumeState, that.previewPlumeState) == 0
				&& previewMaxRows == that.previewMaxRows
				&& mode == that.mode
				&& fallbackMode == that.fallbackMode;
	}

	@Override
	public int hashCode() {
		return Objects.hash(enabled, mode, fallbackMode, diagnosticsEnabled, bodyMeridianSeedCount,
				finSurfaceSeedCount, transonicBandHalfWidth, highAngleDeg, maxTrustedSeparationFraction,
				prestepMach, prestepAngleOfAttackDeg, prestepThetaDeg, prestepPlumeState,
				previewMachMin, previewMachMax, previewMachStep, previewAoADegMin, previewAoADegMax,
				previewAoADegStep, previewThetaDeg, previewPlumeState, previewMaxRows);
	}
}

package info.openrocket.core.util.ejection;

/**
 * All inputs required to size a black-powder ejection charge. All linear
 * dimensions are in metres; mass and force values are SI; the calculator
 * performs internal SI ↔ imperial conversions only inside the Lamé module.
 */
public final class EjectionChargeInputs {

	// --- Bay (the pressurised section) ---
	private String bayName = "Bay";
	private double bayInnerDiameter_m;
	private double bayLength_m;
	private AirframeMaterial bayMaterial = AirframeMaterial.FIBERGLASS;
	private double bayOuterDiameter_m;

	// --- Coupler (or nose-cone shoulder) ---
	private double couplerOuterDiameter_m;
	private double couplerInnerDiameter_m;
	private double couplerEngagementLength_m;
	private AirframeMaterial couplerMaterial = AirframeMaterial.FIBERGLASS;
	private double diametralInterference_m;

	// --- Shear pins ---
	private String shearPinDesignation = "#4-40";
	private int numShearPins = 3;
	private StrengthSource strengthSource = StrengthSource.TESTED_MIN;

	// --- Charge sizing ---
	private double safetyFactor = 1.5;
	private double chuteVolumeFraction = 0.10;
	/**
	 * Absolute packed-chute volume (m³). When > 0 this takes precedence over
	 * {@link #chuteVolumeFraction} and is subtracted directly from the bay
	 * volume to obtain the effective gas volume.
	 */
	private double chutePackedVolume_m3 = 0.0;

	// ----- Bay -----
	public String getBayName() { return bayName; }
	public void setBayName(String v) { this.bayName = v; }

	public double getBayInnerDiameter_m() { return bayInnerDiameter_m; }
	public void setBayInnerDiameter_m(double v) { this.bayInnerDiameter_m = v; }

	public double getBayLength_m() { return bayLength_m; }
	public void setBayLength_m(double v) { this.bayLength_m = v; }

	public AirframeMaterial getBayMaterial() { return bayMaterial; }
	public void setBayMaterial(AirframeMaterial v) { this.bayMaterial = v; }

	public double getBayOuterDiameter_m() { return bayOuterDiameter_m; }
	public void setBayOuterDiameter_m(double v) { this.bayOuterDiameter_m = v; }

	// ----- Coupler -----
	public double getCouplerOuterDiameter_m() { return couplerOuterDiameter_m; }
	public void setCouplerOuterDiameter_m(double v) { this.couplerOuterDiameter_m = v; }

	public double getCouplerInnerDiameter_m() { return couplerInnerDiameter_m; }
	public void setCouplerInnerDiameter_m(double v) { this.couplerInnerDiameter_m = v; }

	public double getCouplerEngagementLength_m() { return couplerEngagementLength_m; }
	public void setCouplerEngagementLength_m(double v) { this.couplerEngagementLength_m = v; }

	public AirframeMaterial getCouplerMaterial() { return couplerMaterial; }
	public void setCouplerMaterial(AirframeMaterial v) { this.couplerMaterial = v; }

	public double getDiametralInterference_m() { return diametralInterference_m; }
	public void setDiametralInterference_m(double v) { this.diametralInterference_m = v; }

	// ----- Shear pins -----
	public String getShearPinDesignation() { return shearPinDesignation; }
	public void setShearPinDesignation(String v) { this.shearPinDesignation = v; }

	public int getNumShearPins() { return numShearPins; }
	public void setNumShearPins(int v) { this.numShearPins = v; }

	public StrengthSource getStrengthSource() { return strengthSource; }
	public void setStrengthSource(StrengthSource v) { this.strengthSource = v; }

	// ----- Sizing -----
	public double getSafetyFactor() { return safetyFactor; }
	public void setSafetyFactor(double v) { this.safetyFactor = v; }

	public double getChuteVolumeFraction() { return chuteVolumeFraction; }
	public void setChuteVolumeFraction(double v) { this.chuteVolumeFraction = v; }

	public double getChutePackedVolume_m3() { return chutePackedVolume_m3; }
	public void setChutePackedVolume_m3(double v) { this.chutePackedVolume_m3 = v; }
}

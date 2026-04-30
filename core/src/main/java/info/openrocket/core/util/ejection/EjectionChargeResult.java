package info.openrocket.core.util.ejection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of {@link EjectionChargeEngine#calculate}: BP mass plus all
 * intermediate physics quantities and any user-facing warnings.
 */
public final class EjectionChargeResult {

	/** Severity of a calculation warning. Drives UI color coding. */
	public enum WarningLevel { CAUTION, WARNING }

	/** Single warning row for the dynamic warnings panel. */
	public static final class Warning {
		private final WarningLevel level;
		private final String message;

		public Warning(WarningLevel level, String message) {
			this.level = level;
			this.message = message;
		}

		public WarningLevel getLevel() { return level; }
		public String getMessage() { return message; }
	}

	private EjectionChargeInputs inputs;

	// Volumes
	private double bayVolume_m3;
	private double chuteVolume_m3;
	private double effectiveVolume_m3;
	private boolean chuteVolumeEstimated;

	// Forces
	private double shearPinForce_N;
	private double couplerFrictionForce_N;
	private double totalSeparationForce_N;
	private boolean usedTheoreticalFallback;

	// Pressure
	private double crossSectionArea_m2;
	private double minRequiredPressure_pa;
	private double workingPressure_pa;

	// Lamé diagnostics
	private double contactPressure_pa;
	private double lameFactorOuter;
	private double lameFactorInner;

	// BP outputs (grams)
	private double bpMassAtWorking_g;
	private double bpMassAtSF15_g;
	private double bpMassAtSF25_g;
	private double molesGasRequired_mol;

	// Airframe burst-stress check
	private double hoopStress_pa;
	private double hoopUts_pa;
	private double hoopStressRatio; // hoopStress / UTS

	private final List<Warning> warnings = new ArrayList<>();

	// --- Accessors ---
	public EjectionChargeInputs getInputs() { return inputs; }
	public void setInputs(EjectionChargeInputs v) { this.inputs = v; }

	public double getBayVolume_m3() { return bayVolume_m3; }
	public void setBayVolume_m3(double v) { this.bayVolume_m3 = v; }

	public double getChuteVolume_m3() { return chuteVolume_m3; }
	public void setChuteVolume_m3(double v) { this.chuteVolume_m3 = v; }

	public double getEffectiveVolume_m3() { return effectiveVolume_m3; }
	public void setEffectiveVolume_m3(double v) { this.effectiveVolume_m3 = v; }

	public boolean isChuteVolumeEstimated() { return chuteVolumeEstimated; }
	public void setChuteVolumeEstimated(boolean v) { this.chuteVolumeEstimated = v; }

	public double getShearPinForce_N() { return shearPinForce_N; }
	public void setShearPinForce_N(double v) { this.shearPinForce_N = v; }

	public double getCouplerFrictionForce_N() { return couplerFrictionForce_N; }
	public void setCouplerFrictionForce_N(double v) { this.couplerFrictionForce_N = v; }

	public double getTotalSeparationForce_N() { return totalSeparationForce_N; }
	public void setTotalSeparationForce_N(double v) { this.totalSeparationForce_N = v; }

	public boolean isUsedTheoreticalFallback() { return usedTheoreticalFallback; }
	public void setUsedTheoreticalFallback(boolean v) { this.usedTheoreticalFallback = v; }

	public double getCrossSectionArea_m2() { return crossSectionArea_m2; }
	public void setCrossSectionArea_m2(double v) { this.crossSectionArea_m2 = v; }

	public double getMinRequiredPressure_pa() { return minRequiredPressure_pa; }
	public void setMinRequiredPressure_pa(double v) { this.minRequiredPressure_pa = v; }

	public double getWorkingPressure_pa() { return workingPressure_pa; }
	public void setWorkingPressure_pa(double v) { this.workingPressure_pa = v; }

	public double getContactPressure_pa() { return contactPressure_pa; }
	public void setContactPressure_pa(double v) { this.contactPressure_pa = v; }

	public double getLameFactorOuter() { return lameFactorOuter; }
	public void setLameFactorOuter(double v) { this.lameFactorOuter = v; }

	public double getLameFactorInner() { return lameFactorInner; }
	public void setLameFactorInner(double v) { this.lameFactorInner = v; }

	public double getBpMassAtWorking_g() { return bpMassAtWorking_g; }
	public void setBpMassAtWorking_g(double v) { this.bpMassAtWorking_g = v; }

	public double getBpMassAtSF15_g() { return bpMassAtSF15_g; }
	public void setBpMassAtSF15_g(double v) { this.bpMassAtSF15_g = v; }

	public double getBpMassAtSF25_g() { return bpMassAtSF25_g; }
	public void setBpMassAtSF25_g(double v) { this.bpMassAtSF25_g = v; }

	public double getMolesGasRequired_mol() { return molesGasRequired_mol; }
	public void setMolesGasRequired_mol(double v) { this.molesGasRequired_mol = v; }

	public double getHoopStress_pa() { return hoopStress_pa; }
	public void setHoopStress_pa(double v) { this.hoopStress_pa = v; }

	public double getHoopUts_pa() { return hoopUts_pa; }
	public void setHoopUts_pa(double v) { this.hoopUts_pa = v; }

	public double getHoopStressRatio() { return hoopStressRatio; }
	public void setHoopStressRatio(double v) { this.hoopStressRatio = v; }

	public List<Warning> getWarnings() { return Collections.unmodifiableList(warnings); }
	public void addWarning(WarningLevel level, String msg) {
		warnings.add(new Warning(level, msg));
	}
}

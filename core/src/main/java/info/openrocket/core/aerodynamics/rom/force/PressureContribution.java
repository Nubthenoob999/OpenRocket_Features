package info.openrocket.core.aerodynamics.rom.force;

public class PressureContribution {
	private final String componentName;
	private final double deltaCA;
	private final double deltaCN;
	private final double deltaCm;
	private final double xCp;

	public PressureContribution(String componentName, double deltaCA, double deltaCN, double deltaCm, double xCp) {
		this.componentName = componentName;
		this.deltaCA = deltaCA;
		this.deltaCN = deltaCN;
		this.deltaCm = deltaCm;
		this.xCp = xCp;
	}

	public String getComponentName() {
		return componentName;
	}

	public double getDeltaCA() {
		return deltaCA;
	}

	public double getDeltaCN() {
		return deltaCN;
	}

	public double getDeltaCm() {
		return deltaCm;
	}

	public double getXCp() {
		return xCp;
	}
}

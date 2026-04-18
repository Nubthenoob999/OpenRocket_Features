package info.openrocket.core.aerodynamics.rom.force;

public class FrictionContribution {
	private final String componentName;
	private final double deltaCA;
	private final double deltaCm;
	private final double xCp;

	public FrictionContribution(String componentName, double deltaCA, double deltaCm, double xCp) {
		this.componentName = componentName;
		this.deltaCA = deltaCA;
		this.deltaCm = deltaCm;
		this.xCp = xCp;
	}

	public String getComponentName() {
		return componentName;
	}

	public double getDeltaCA() {
		return deltaCA;
	}

	public double getDeltaCm() {
		return deltaCm;
	}

	public double getXCp() {
		return xCp;
	}
}

package info.openrocket.core.aerodynamics.rom.geometry;

public class FinGeometry {
	private final String componentName;
	private final double xStart;
	private final double rootChord;
	private final double tipChord;
	private final double sweep;
	private final double span;
	private final double thickness;
	private final double cantAngleRad;
	private final double planformArea;
	private final int finCount;
	private final double bodyRadiusAtRoot;
	private final double centroidX;

	public FinGeometry(String componentName, double xStart, double rootChord, double tipChord, double sweep,
			double span, double thickness, double cantAngleRad, double planformArea, int finCount,
			double bodyRadiusAtRoot, double centroidX) {
		this.componentName = componentName;
		this.xStart = xStart;
		this.rootChord = rootChord;
		this.tipChord = tipChord;
		this.sweep = sweep;
		this.span = span;
		this.thickness = thickness;
		this.cantAngleRad = cantAngleRad;
		this.planformArea = planformArea;
		this.finCount = finCount;
		this.bodyRadiusAtRoot = bodyRadiusAtRoot;
		this.centroidX = centroidX;
	}

	public String getComponentName() {
		return componentName;
	}

	public double getXStart() {
		return xStart;
	}

	public double getRootChord() {
		return rootChord;
	}

	public double getTipChord() {
		return tipChord;
	}

	public double getSweep() {
		return sweep;
	}

	public double getSpan() {
		return span;
	}

	public double getThickness() {
		return thickness;
	}

	public double getCantAngleRad() {
		return cantAngleRad;
	}

	public double getPlanformArea() {
		return planformArea;
	}

	public int getFinCount() {
		return finCount;
	}

	public double getBodyRadiusAtRoot() {
		return bodyRadiusAtRoot;
	}

	public double getCentroidX() {
		return centroidX;
	}
}

package info.openrocket.core.aerodynamics.rom.flow;

public class EdgeState {
	private final double edgeVelocity;
	private final double edgeMach;
	private final double pressureCoefficient;
	private final double localIncidenceRad;
	private final double velocityGradient;
	private final boolean valid;

	public EdgeState(double edgeVelocity, double edgeMach, double pressureCoefficient, double localIncidenceRad,
			double velocityGradient, boolean valid) {
		this.edgeVelocity = edgeVelocity;
		this.edgeMach = edgeMach;
		this.pressureCoefficient = pressureCoefficient;
		this.localIncidenceRad = localIncidenceRad;
		this.velocityGradient = velocityGradient;
		this.valid = valid;
	}

	public double getEdgeVelocity() {
		return edgeVelocity;
	}

	public double getEdgeMach() {
		return edgeMach;
	}

	public double getPressureCoefficient() {
		return pressureCoefficient;
	}

	public double getLocalIncidenceRad() {
		return localIncidenceRad;
	}

	public double getVelocityGradient() {
		return velocityGradient;
	}

	public boolean isValid() {
		return valid;
	}
}

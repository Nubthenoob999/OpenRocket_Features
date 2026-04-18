package info.openrocket.core.aerodynamics.rom.bl;

public class BoundaryLayerState {
	private final double displacementThickness;
	private final double momentumThickness;
	private final double shapeFactor;
	private final double skinFrictionCoefficient;
	private final boolean turbulent;
	private final boolean transitioned;
	private final boolean separated;
	private final boolean valid;
	private final double stiffnessIndicator;
	private final String notes;

	public BoundaryLayerState(double displacementThickness, double momentumThickness, double shapeFactor,
			double skinFrictionCoefficient, boolean turbulent, boolean transitioned, boolean separated,
			boolean valid, double stiffnessIndicator, String notes) {
		this.displacementThickness = displacementThickness;
		this.momentumThickness = momentumThickness;
		this.shapeFactor = shapeFactor;
		this.skinFrictionCoefficient = skinFrictionCoefficient;
		this.turbulent = turbulent;
		this.transitioned = transitioned;
		this.separated = separated;
		this.valid = valid;
		this.stiffnessIndicator = stiffnessIndicator;
		this.notes = notes != null ? notes : "";
	}

	public BoundaryLayerState(double momentumThickness, double shapeFactor, boolean turbulent,
			boolean separated, double skinFrictionCoefficient, double stiffnessIndicator, boolean valid) {
		this(shapeFactor * Math.max(0.0, momentumThickness), momentumThickness, shapeFactor,
				skinFrictionCoefficient, turbulent, turbulent, separated, valid, stiffnessIndicator, "");
	}

	public static BoundaryLayerState invalid() {
		return new BoundaryLayerState(0.0, 0.0, 9.0, 1e-6, false, false,
				true, false, 1.0, "invalid");
	}

	public double getDisplacementThickness() {
		return displacementThickness;
	}

	public double getMomentumThickness() {
		return momentumThickness;
	}

	public double getShapeFactor() {
		return shapeFactor;
	}

	public double getSkinFrictionCoefficient() {
		return skinFrictionCoefficient;
	}

	public boolean isTurbulent() {
		return turbulent;
	}

	public boolean isTransitioned() {
		return transitioned;
	}

	public boolean isSeparated() {
		return separated;
	}

	public boolean isValid() {
		return valid;
	}

	public double getStiffnessIndicator() {
		return stiffnessIndicator;
	}

	public String getNotes() {
		return notes;
	}
}

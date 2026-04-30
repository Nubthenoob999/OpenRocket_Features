package info.openrocket.core.util.ejection;

/**
 * Material categories for rocket airframe tubes and couplers used by the
 * ejection charge calculator. Material identity drives both the friction
 * coefficient lookup (FrictionCoefficientLookupTable) and the elastic
 * properties used in the Lamé interference-fit calculation
 * (RocketryMaterialProperties).
 */
public enum AirframeMaterial {
	FIBERGLASS("Fiberglass"),
	CARBON_FIBER("Carbon fiber"),
	PHENOLIC("Phenolic"),
	CARDBOARD("Cardboard / kraft"),
	BALSA_WOOD("Balsa wood"),
	HARDWOOD("Hardwood"),
	BLUE_TUBE("Blue Tube"),
	PLASTIC_NC("Plastic (nose cone)");

	private final String displayName;

	AirframeMaterial(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}

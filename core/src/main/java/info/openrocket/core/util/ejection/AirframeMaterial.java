package info.openrocket.core.util.ejection;

import java.util.Locale;

import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;

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
	PLASTIC_NC("Plastic (nose cone)"),
	METAL("Metal");

	private final String displayName;

	AirframeMaterial(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Classify a component material for the friction-coefficient table.  This
	 * category does not supply the component's stiffness or strength when the
	 * material record contains those properties.
	 *
	 * @return the closest friction category, or {@code null} when no safe
	 * category can be inferred
	 */
	public static AirframeMaterial fromMaterial(Material material) {
		if (material == null) {
			return null;
		}
		String name = material.getName().toLowerCase(Locale.ROOT);
		if (name.contains("blue tube")) return BLUE_TUBE;
		if (name.contains("carbon")) return CARBON_FIBER;
		if (name.contains("fiberglass") || name.contains("glass fiber")
				|| name.contains("g10") || name.contains("g-10") || name.contains("fr4")
				|| name.contains("fr-4")) return FIBERGLASS;
		if (name.contains("phenolic")) return PHENOLIC;
		if (name.contains("balsa")) return BALSA_WOOD;
		if (name.contains("cardboard") || name.contains("kraft") || name.contains("paper")) {
			return CARDBOARD;
		}

		MaterialGroup group = material.getGroup();
		if (group == MaterialGroup.METALS) return METAL;
		if (group == MaterialGroup.PLASTICS) return PLASTIC_NC;
		if (group == MaterialGroup.WOODS) return HARDWOOD;
		if (group == MaterialGroup.PAPER) return CARDBOARD;
		return null;
	}

	@Override
	public String toString() {
		return displayName;
	}
}

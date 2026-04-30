package info.openrocket.core.util.ejection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lookup table of shear-pin specifications for hobby rocketry. Single-shear
 * strength values are cited per the pseudocode source set: Feretich
 * (theoretical, Nylon 6/6), RocketMaterials.org (tested), Northwest Rocketry,
 * Rocketry Forum community consensus.
 *
 * <p>All strength values are lbs/pin in single-shear configuration.
 * Designations preserve the conventions used in the pseudocode, so existing
 * documentation and saved configurations remain usable.
 */
public final class ShearPinLookupTable {

	private static final Map<String, ShearPinSpec> TABLE = buildTable();

	private ShearPinLookupTable() {
	}

	private static Map<String, ShearPinSpec> buildTable() {
		LinkedHashMap<String, ShearPinSpec> t = new LinkedHashMap<>();

		// =====================================================================
		// IMPERIAL NYLON 6/6 SCREWS
		// =====================================================================
		t.put("#2-56", new ShearPinSpec(
				"#2-56", PinMaterial.NYLON_6_6_IMPERIAL,
				0.0860, 0.0744, 0.0628,
				26.0, 39.0, 23.4, 28.6,
				"38mm – 54mm airframe",
				"Most common low-end shear pin. Standard for 38mm/54mm rockets."));

		t.put("#4-40", new ShearPinSpec(
				"#4-40", PinMaterial.NYLON_6_6_IMPERIAL,
				0.1120, 0.0985, 0.0849,
				47.0, 73.0, 38.2, 46.8,
				"3\" – 4\" airframe",
				"Workhorse pin for 3\"–4\" HPR dual-deploy bays."));

		t.put("#6-32", new ShearPinSpec(
				"#6-32", PinMaterial.NYLON_6_6_IMPERIAL,
				0.1380, 0.1218, 0.1055,
				69.0, 110.0, 56.4, 70.4,
				"4\" – 6\" airframe",
				"For larger HPR bays. Use multiples of 3 evenly spaced."));

		t.put("#8-32", new ShearPinSpec(
				"#8-32", PinMaterial.NYLON_6_6_IMPERIAL,
				0.1640, 0.1437, 0.1234,
				97.0, 153.0, 79.0, 99.0,
				"6\" airframe",
				"Heavy 6\" rockets only. Verify with ground test."));

		t.put("#10-24", new ShearPinSpec(
				"#10-24", PinMaterial.NYLON_6_6_IMPERIAL,
				0.1900, 0.1629, 0.1359,
				115.0, 195.0, 100.0, 130.0,
				"6\"+ airframe",
				"Very large/heavy rockets only. Rarely needed; "
						+ "consider multiple #6-32 instead."));

		// =====================================================================
		// METRIC NYLON 6/6 SCREWS (theoretical only — no tested data)
		// =====================================================================
		t.put("M2", new ShearPinSpec(
				"M2", PinMaterial.NYLON_6_6_METRIC,
				0.07874, 0.06851, 0.05942,
				27.0, 39.0, 0.0, 0.0,
				"38mm – 54mm airframe",
				"Near-equivalent to #2-56 imperial. Common in European builds."));

		t.put("M2.5", new ShearPinSpec(
				"M2.5", PinMaterial.NYLON_6_6_METRIC,
				0.09843, 0.08532, 0.07402,
				38.0, 60.0, 0.0, 0.0,
				"54mm – 3\" airframe",
				"Falls between M2 and M3. Good for 54mm rockets where M3 is too strong."));

		t.put("M3", new ShearPinSpec(
				"M3", PinMaterial.NYLON_6_6_METRIC,
				0.11811, 0.10532, 0.09396,
				67.0, 91.0, 0.0, 0.0,
				"3\" – 4\" airframe",
				"Near-equivalent to #4-40 imperial. Standard metric HPR dual-deploy choice."));

		// =====================================================================
		// STYRENE RODS (theoretical only)
		// =====================================================================
		t.put("Styrene-0.060", new ShearPinSpec(
				"0.060\" Styrene Rod", PinMaterial.STYRENE_ROD,
				0.0600, 0.0, 0.0,
				2.4, 11.9, 0.0, 0.0,
				"24mm – 29mm airframe",
				"Very light rockets only. Plastruct item."));

		t.put("Styrene-1/16", new ShearPinSpec(
				"1/16\" Styrene Rod", PinMaterial.STYRENE_ROD,
				0.0625, 0.0, 0.0,
				2.8, 12.9, 0.0, 0.0,
				"29mm – 54mm light airframe",
				"~40% of #2-56 nylon strength. Evergreen #222."));

		t.put("Styrene-3/32", new ShearPinSpec(
				"3/32\" Styrene Rod", PinMaterial.STYRENE_ROD,
				0.09375, 0.0, 0.0,
				6.2, 29.0, 0.0, 0.0,
				"54mm – 3\" airframe",
				"~Equivalent shear to #2-56 nylon."));

		t.put("Styrene-1/8", new ShearPinSpec(
				"1/8\" Styrene Rod", PinMaterial.STYRENE_ROD,
				0.1250, 0.0, 0.0,
				11.0, 51.5, 0.0, 0.0,
				"3\" – 4\" airframe",
				"~Equivalent to #4-40 nylon at mid-grade styrene."));

		// =====================================================================
		// ABS RODS (theoretical only)
		// =====================================================================
		t.put("ABS-1/16", new ShearPinSpec(
				"1/16\" ABS Rod", PinMaterial.ABS_ROD,
				0.0625, 0.0, 0.0,
				8.0, 14.8, 0.0, 0.0,
				"29mm – 38mm airframe",
				"Softer shear than nylon. Good in fiberglass tubes."));

		t.put("ABS-3/32", new ShearPinSpec(
				"3/32\" ABS Rod", PinMaterial.ABS_ROD,
				0.09375, 0.0, 0.0,
				18.6, 33.1, 0.0, 0.0,
				"54mm airframe",
				"~Equivalent to #2-56 nylon."));

		t.put("ABS-1/8", new ShearPinSpec(
				"1/8\" ABS Rod", PinMaterial.ABS_ROD,
				0.1250, 0.0, 0.0,
				33.1, 58.8, 0.0, 0.0,
				"3\" airframe",
				"~Equivalent to #4-40 nylon. Clean shear in fiberglass tubes."));

		return Collections.unmodifiableMap(t);
	}

	/**
	 * Look up a pin spec by its key (e.g. {@code "#4-40"}, {@code "M3"}).
	 *
	 * @throws IllegalArgumentException if the key is unknown
	 */
	public static ShearPinSpec getSpec(String designation) {
		ShearPinSpec spec = TABLE.get(designation);
		if (spec == null) {
			throw new IllegalArgumentException("Unknown pin designation: " + designation);
		}
		return spec;
	}

	/** All known designations in insertion order (suitable for a UI dropdown). */
	public static List<String> getAllDesignations() {
		return Collections.unmodifiableList(new java.util.ArrayList<>(TABLE.keySet()));
	}

	/** All pin specs filtered by material category, in insertion order. */
	public static List<ShearPinSpec> getPinsByMaterial(PinMaterial material) {
		return TABLE.values().stream()
				.filter(s -> s.getMaterial() == material)
				.collect(Collectors.toList());
	}

	/**
	 * Total ejection force (lbs) required to shear all pins, for the given
	 * designation, count, and strength source. No friction add-on is applied
	 * here; the engine handles coupler friction separately via Lamé theory.
	 */
	public static double calcTotalShearForce(String designation,
											 int quantity,
											 StrengthSource source) {
		if (quantity < 0) {
			throw new IllegalArgumentException("Pin quantity must be non-negative");
		}
		ShearPinSpec spec = getSpec(designation);
		return spec.getShearStrength(source) * quantity;
	}
}

package info.openrocket.core.util.ejection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Static and kinetic coefficient-of-friction lookup for common airframe
 * material pairings used in coupler/shoulder mating surfaces. Values are
 * compiled from the source set listed in the pseudocode header
 * (tribology studies, Engineering Toolbox, community empirical reports,
 * Feretich.com, wood/cardboard friction literature).
 *
 * <p>All values are dry-condition nominal estimates. Always ground test where
 * possible — measured COF for a specific tube/coupler can vary ±0.10 from the
 * tabulated values.
 */
public final class FrictionCoefficientLookupTable {

	private static final Map<String, MaterialPairCOF> TABLE = buildTable();

	private static final MaterialPairCOF DEFAULT_FALLBACK = new MaterialPairCOF(
			null, null,
			0.30, 0.40, 0.50,
			0.20, 0.30, 0.40,
			"Low",
			"Generic fallback estimate",
			"No specific data for this material pair; verify by ground test.");

	private FrictionCoefficientLookupTable() {
	}

	/**
	 * Build a canonical alphabetical key for a material pair so that
	 * {@code FG_on_PHENOLIC} and {@code PHENOLIC_on_FG} resolve to the same
	 * entry. Self-pairs are returned as {@code MAT_on_MAT}.
	 */
	public static String buildKey(AirframeMaterial a, AirframeMaterial b) {
		String an = a.name();
		String bn = b.name();
		if (an.compareTo(bn) <= 0) {
			return an + "_on_" + bn;
		}
		return bn + "_on_" + an;
	}

	private static void put(Map<String, MaterialPairCOF> t,
							AirframeMaterial a, AirframeMaterial b,
							double sMin, double sNom, double sMax,
							double kMin, double kNom, double kMax,
							String confidence, String sources, String notes) {
		t.put(buildKey(a, b),
				new MaterialPairCOF(a, b, sMin, sNom, sMax, kMin, kNom, kMax,
						confidence, sources, notes));
	}

	private static Map<String, MaterialPairCOF> buildTable() {
		HashMap<String, MaterialPairCOF> t = new HashMap<>();

		// ---- Self pairs ----
		put(t, AirframeMaterial.FIBERGLASS, AirframeMaterial.FIBERGLASS,
				0.25, 0.35, 0.45, 0.18, 0.28, 0.38,
				"High", "MDPI tribology 2022–2025; rocketry forum",
				"Most common HPR pairing. Slightly higher COF if mould-finish glossy.");

		put(t, AirframeMaterial.CARBON_FIBER, AirframeMaterial.CARBON_FIBER,
				0.18, 0.25, 0.32, 0.12, 0.18, 0.25,
				"High", "DTIC AD0672799; ScienceDirect 2023 CFRP dry-sliding",
				"Graphite transfer film forms during sliding — self-lubricating.");

		put(t, AirframeMaterial.PHENOLIC, AirframeMaterial.PHENOLIC,
				0.28, 0.35, 0.45, 0.20, 0.28, 0.38,
				"Medium", "Engineering Toolbox; Feretich.com",
				"Spiral-wound surface raises asperity interlocking.");

		put(t, AirframeMaterial.CARDBOARD, AirframeMaterial.CARDBOARD,
				0.40, 0.50, 0.65, 0.30, 0.40, 0.55,
				"Medium", "Sagepub 2025 paper-to-paper; STFI",
				"High variance. CA-hardened holes reduce COF 10–20%. Moisture raises COF.");

		put(t, AirframeMaterial.BLUE_TUBE, AirframeMaterial.BLUE_TUBE,
				0.12, 0.16, 0.25, 0.10, 0.14, 0.22,
				"Medium", "Vulcanex vulcanized-fibre engineering data",
				"Supplier reports 0.16 fibre-on-fibre; range covers finish and moisture variation.");

		put(t, AirframeMaterial.HARDWOOD, AirframeMaterial.HARDWOOD,
				0.30, 0.40, 0.50, 0.20, 0.30, 0.40,
				"Medium", "Forestry & Wood Science journals",
				"Sanded hardwood; grain direction matters.");

		put(t, AirframeMaterial.BALSA_WOOD, AirframeMaterial.BALSA_WOOD,
				0.35, 0.45, 0.55, 0.25, 0.35, 0.45,
				"Low", "Wood friction literature",
				"Soft surface — easily marred. Verify by ground test.");

		put(t, AirframeMaterial.PLASTIC_NC, AirframeMaterial.PLASTIC_NC,
				0.28, 0.35, 0.42, 0.20, 0.28, 0.36,
				"Medium", "Engineering Toolbox plastic-on-plastic",
				"Generic ABS/PETG/PLA estimate.");

		// ---- Common cross pairs (FG hub typical) ----
		put(t, AirframeMaterial.FIBERGLASS, AirframeMaterial.PHENOLIC,
				0.27, 0.35, 0.43, 0.18, 0.28, 0.38,
				"Medium", "Engineering Toolbox; community",
				"Common when phenolic coupler is used in a fiberglass tube.");

		put(t, AirframeMaterial.FIBERGLASS, AirframeMaterial.CARBON_FIBER,
				0.20, 0.28, 0.36, 0.15, 0.22, 0.30,
				"Medium", "Composite tribology studies",
				"CFRP coupler in FG tube is a low-friction pairing.");

		put(t, AirframeMaterial.FIBERGLASS, AirframeMaterial.CARDBOARD,
				0.30, 0.40, 0.50, 0.22, 0.32, 0.42,
				"Low", "Estimate from constituent self-pairs",
				"Cardboard coupler in FG tube — verify by ground test.");

		put(t, AirframeMaterial.FIBERGLASS, AirframeMaterial.BLUE_TUBE,
				0.28, 0.36, 0.45, 0.20, 0.28, 0.36,
				"Low", "Estimate from constituent self-pairs",
				"Blue Tube coupler in FG tube — common LPR/MPR pairing.");

		put(t, AirframeMaterial.PHENOLIC, AirframeMaterial.CARDBOARD,
				0.32, 0.42, 0.55, 0.24, 0.33, 0.45,
				"Low", "Estimate from constituent self-pairs",
				"Cardboard coupler in phenolic tube.");

		put(t, AirframeMaterial.CARBON_FIBER, AirframeMaterial.PHENOLIC,
				0.22, 0.30, 0.38, 0.15, 0.23, 0.30,
				"Low", "Estimate from constituent self-pairs",
				"Phenolic coupler in CFRP tube.");

		put(t, AirframeMaterial.PLASTIC_NC, AirframeMaterial.FIBERGLASS,
				0.24, 0.32, 0.40, 0.16, 0.24, 0.32,
				"Low", "Estimate from constituent self-pairs",
				"Plastic nose-cone shoulder in FG tube.");

		put(t, AirframeMaterial.PLASTIC_NC, AirframeMaterial.CARDBOARD,
				0.30, 0.38, 0.48, 0.22, 0.30, 0.40,
				"Low", "Estimate from constituent self-pairs",
				"Plastic nose-cone shoulder in cardboard tube.");

		return Collections.unmodifiableMap(t);
	}

	/** Look up by canonical "MAT1_on_MAT2" key. Falls back to a generic estimate. */
	public static MaterialPairCOF getCOF(String key) {
		MaterialPairCOF cof = TABLE.get(key);
		return cof != null ? cof : DEFAULT_FALLBACK;
	}

	/** Look up by enum pair (order-insensitive). Falls back to a generic estimate. */
	public static MaterialPairCOF getCOF(AirframeMaterial a, AirframeMaterial b) {
		return getCOF(buildKey(a, b));
	}

	/** True if a tabulated entry exists for this pair (vs. the fallback estimate). */
	public static boolean hasEntry(AirframeMaterial a, AirframeMaterial b) {
		return TABLE.containsKey(buildKey(a, b));
	}
}

package info.openrocket.core.file.openrocket.importt;

import java.util.HashMap;
import java.util.Locale;

import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.database.Databases;
import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Reflection;

////MaterialSetter  -  sets a Material value
class MaterialSetter implements Setter {
	private final Reflection.Method setMethod;
	private final Material.Type type;

	public MaterialSetter(Reflection.Method set, Material.Type type) {
		this.setMethod = set;
		this.type = type;
	}

	@Override
	public void set(RocketComponent c, String name, HashMap<String, String> attributes,
			WarningSet warnings) {

		Material mat;

		// Check name != ""
		name = name.trim();
		if (name.isEmpty()) {
			warnings.add(Warning.fromString("Illegal material specification, ignoring."));
			return;
		}

		// Parse density
		double density;
		String str;
		str = attributes.remove("density");
		if (str == null) {
			warnings.add(Warning.fromString("Illegal material specification, ignoring."));
			return;
		}
		try {
			density = Double.parseDouble(str);
		} catch (NumberFormatException e) {
			warnings.add(Warning.fromString("Illegal material specification, ignoring."));
			return;
		}

		// Parse shear modulus (optional; only use when explicitly provided)
		Double shearModulus = null;
		str = attributes.remove("shearModulus");
		if (str != null) {
			try {
				shearModulus = Double.parseDouble(str);
			} catch (NumberFormatException e) {
				warnings.add(Warning.fromString("Illegal shear modulus value, using 0.0."));
				shearModulus = 0.0;
			}
		}

		Double youngsModulus = parseOptionalProperty(attributes, "youngsModulus", "Young's modulus", warnings);
		Double tensileStrength = parseOptionalProperty(attributes, "tensileStrength", "Tensile strength", warnings);
		Double compressiveStrength = parseOptionalProperty(attributes, "compressiveStrength", "Compressive strength", warnings);
		Double poissonRatio = parseOptionalProperty(attributes, "poissonRatio", "Poisson ratio", warnings);

		// Parse thickness
		// double thickness = 0;
		// str = attributes.remove("thickness");
		// try {
		// if (str != null)
		// thickness = Double.parseDouble(str);
		// } catch (NumberFormatException e){
		// warnings.add(Warning.fromString("Illegal material specification,
		// ignoring."));
		// return;
		// }

		// Check type if specified
		str = attributes.remove("type");
		if (str != null && !type.name().toLowerCase(Locale.ENGLISH).equals(str)) {
			warnings.add(Warning.fromString("Illegal material type specified, ignoring."));
			return;
		}

		// Check for material group
		str = attributes.remove("group");
		MaterialGroup group = null;
		if (str != null) {
			try {
				group = MaterialGroup.loadFromDatabaseStringWithBackwardCompatibility(str, type, name, density);
			} catch (IllegalArgumentException e) {
				warnings.add(Warning.fromString("Illegal material group specified, ignoring."));
			}
		}

		if (youngsModulus != null || tensileStrength != null || compressiveStrength != null || poissonRatio != null) {
			mat = Databases.findMaterial(type, name, density, shearModulus == null ? 0.0 : shearModulus,
					youngsModulus == null ? Double.NaN : youngsModulus,
					tensileStrength == null ? Double.NaN : tensileStrength,
					compressiveStrength == null ? Double.NaN : compressiveStrength,
					poissonRatio == null ? Double.NaN : poissonRatio, group);
		} else if (shearModulus == null) {
			mat = Databases.findMaterial(type, name, density, group);
		} else {
			mat = Databases.findMaterial(type, name, density, shearModulus, group);
		}

		setMethod.invoke(c, mat);
	}

	private static Double parseOptionalProperty(HashMap<String, String> attributes, String attributeName,
			String displayName, WarningSet warnings) {
		String value = attributes.remove(attributeName);
		if (value == null) {
			return null;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			warnings.add(Warning.fromString("Illegal " + displayName + " value, omitting it."));
			return null;
		}
	}
}

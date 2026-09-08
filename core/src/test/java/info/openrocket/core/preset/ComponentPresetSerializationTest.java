package info.openrocket.core.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;
import info.openrocket.core.motor.Manufacturer;

class ComponentPresetSerializationTest {

	@Test
	void structuralMaterialPropertiesSurviveJavaSerialization() throws Exception {
		Material material = Material.newMaterial(Material.Type.BULK, "Preset structural material",
				1800.0, 5.5e9, 70.0e9, 600.0e6, 450.0e6, 0.30,
				MaterialGroup.COMPOSITES, true, true);
		TypedPropertyMap specification = new TypedPropertyMap();
		specification.put(ComponentPreset.TYPE, ComponentPreset.Type.BODY_TUBE);
		specification.put(ComponentPreset.MANUFACTURER, Manufacturer.getManufacturer("Test manufacturer"));
		specification.put(ComponentPreset.PARTNO, "TEST-001");
		specification.put(ComponentPreset.LENGTH, 1.0);
		specification.put(ComponentPreset.OUTER_DIAMETER, 0.1);
		specification.put(ComponentPreset.THICKNESS, 0.002);
		specification.put(ComponentPreset.MATERIAL, material);
		ComponentPreset original = ComponentPresetFactory.create(specification);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(original);
		}
		ComponentPreset restored;
		try (ObjectInputStream input = new ObjectInputStream(
				new ByteArrayInputStream(bytes.toByteArray()))) {
			restored = (ComponentPreset) input.readObject();
		}
		Material restoredMaterial = restored.get(ComponentPreset.MATERIAL);

		assertEquals(1800.0, restoredMaterial.getDensity(), 1.0e-12);
		assertEquals(5.5e9, restoredMaterial.getInPlaneShearModulus(), 1.0e-3);
		assertEquals(70.0e9, restoredMaterial.getYoungsModulus(), 1.0e-3);
		assertEquals(600.0e6, restoredMaterial.getTensileStrength(), 1.0e-3);
		assertEquals(450.0e6, restoredMaterial.getCompressiveStrength(), 1.0e-3);
		assertEquals(0.30, restoredMaterial.getPoissonRatio(), 1.0e-12);
	}
}


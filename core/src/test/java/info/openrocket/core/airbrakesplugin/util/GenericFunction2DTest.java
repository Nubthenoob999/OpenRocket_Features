package info.openrocket.core.airbrakesplugin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericFunction2DTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void loadsAbsoluteForceAndNormalizesPercentDeployment() throws Exception {
		Path surface = write("absolute-force.csv", """
				Mach,DeploymentPercentage,Drag_N
				0.25,0,10
				0.50,0,20
				0.25,100,30
				0.50,100,50
				""");

		GenericFunction2D function = GenericFunction2D.fromCsv(surface, ExtrapolationType.CONSTANT);

		assertEquals(27.5, function.dragN(0.375, 0.5), 1.0e-12);
		assertEquals(2, function.xAxis().length);
		assertEquals(2, function.yAxis().length);
		assertEquals(0.0, function.yAxis()[0], 0.0);
		assertEquals(1.0, function.yAxis()[1], 0.0);
	}

	@Test
	void selectsAbsoluteForceWhenCoefficientIsAlsoPresent() throws Exception {
		Path surface = write("force-and-coefficient.csv", """
				Mach,Deployment,CD,Drag_N
				0.25,0,99,10
				0.50,0,99,20
				0.25,1,99,30
				0.50,1,99,50
				""");

		GenericFunction2D function = GenericFunction2D.fromCsv(surface, ExtrapolationType.CONSTANT);

		assertEquals(10.0, function.dragN(0.25, 0.0), 1.0e-9);
		assertEquals(50.0, function.dragN(0.50, 1.0), 1.0e-9);
	}

	@Test
	void bilinearCellsAndConstantEdgesAreContinuous() throws Exception {
		Path surface = write("continuous-force.csv", """
				Mach,Deployment,Drag_N
				0.0,0.0,0
				0.5,0.0,10
				1.0,0.0,20
				0.0,1.0,20
				0.5,1.0,30
				1.0,1.0,40
				""");
		GenericFunction2D function = GenericFunction2D.fromCsv(surface, ExtrapolationType.CONSTANT);
		double epsilon = 1.0e-9;

		assertEquals(function.dragN(0.0, 0.4), function.dragN(-epsilon, 0.4), 0.0);
		assertEquals(function.dragN(1.0, 0.4), function.dragN(1.0 + epsilon, 0.4), 0.0);
		assertEquals(function.dragN(0.3, 0.0), function.dragN(0.3, -epsilon), 0.0);
		assertEquals(function.dragN(0.3, 1.0), function.dragN(0.3, 1.0 + epsilon), 0.0);
		assertEquals(function.dragN(0.5 - epsilon, 0.4), function.dragN(0.5 + epsilon, 0.4), 5.0e-8);
	}

	@Test
	void rejectsCoefficientOnlySurface() throws Exception {
		Path coefficientOnly = write("coefficient-only.csv", """
				Deployment Level,Mach,CD
				0,0.25,0.40
				0,0.50,0.35
				1,0.25,0.80
				1,0.50,0.70
				""");

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> GenericFunction2D.fromCsv(coefficientOnly, ExtrapolationType.CONSTANT));
		assertTrue(failure.getMessage().contains("drag/force column"), failure.getMessage());
	}

	private Path write(String name, String contents) throws Exception {
		Path path = temporaryDirectory.resolve(name);
		Files.writeString(path, contents, StandardCharsets.UTF_8);
		return path;
	}
}

package info.openrocket.core.tuning;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.database.ComponentPresetDao;
import info.openrocket.core.database.ComponentPresetDatabase;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSQLiteDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.preset.ComponentPreset;
import info.openrocket.core.preset.xml.OpenRocketComponentLoader;
import info.openrocket.core.startup.Application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

final class TuningTestInfrastructure {

	private static volatile boolean initialized;

	private TuningTestInfrastructure() {
	}

	static synchronized void ensureApplicationInjector() throws IOException {
		if (initialized && Application.getInjector() != null) {
			return;
		}

		Path coreRoot = findCoreModuleRoot();
		ComponentPresetDatabase componentPresetDatabase = loadComponentPresetDatabase(coreRoot);
		ThrustCurveMotorSetDatabase motorDatabase = loadMotorDatabase(coreRoot);

		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Module dbOverrides = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ComponentPresetDao.class).toInstance(componentPresetDatabase);
				bind(ThrustCurveMotorSetDatabase.class).toInstance(motorDatabase);
				bind(MotorDatabase.class).to(ThrustCurveMotorSetDatabase.class);
			}
		};

		Injector injector = Guice.createInjector(Modules.override(applicationModule).with(dbOverrides), pluginModule);
		Application.setInjector(injector);
		initialized = true;
	}

	private static ComponentPresetDatabase loadComponentPresetDatabase(Path coreRoot) throws IOException {
		Path presetsDir = coreRoot.resolve("src/main/resources/datafiles/components");
		if (!Files.isDirectory(presetsDir)) {
			throw new IOException("Component preset directory not found: " + presetsDir);
		}

		ComponentPresetDatabase dao = new ComponentPresetDatabase();
		OpenRocketComponentLoader loader = new OpenRocketComponentLoader();

		try (Stream<Path> files = Files.walk(presetsDir)) {
			List<Path> presetFiles = files
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".orc"))
					.sorted()
					.toList();

			for (Path file : presetFiles) {
				try (InputStream is = Files.newInputStream(file)) {
					Collection<ComponentPreset> presets = loader.load(is, file.getFileName().toString());
					dao.addAll(presets);
				}
			}
		}

		return dao;
	}

	private static ThrustCurveMotorSetDatabase loadMotorDatabase(Path coreRoot) throws IOException {
		Path bundledDb = coreRoot.resolve("src/main/resources/datafiles/thrustcurves/initial_motors.db");
		if (!Files.isRegularFile(bundledDb)) {
			throw new IOException("Bundled motor database not found: " + bundledDb);
		}

		Path tmpDir = coreRoot.resolve("build/tmp/tuning-test-infrastructure");
		Files.createDirectories(tmpDir);
		Path dbCopy = tmpDir.resolve("initial_motors.db");
		Files.copy(bundledDb, dbCopy, StandardCopyOption.REPLACE_EXISTING);

		List<ThrustCurveMotor> motors;
		try {
			motors = ThrustCurveMotorSQLiteDatabase.readDatabase(dbCopy.toFile());
		} catch (Exception e) {
			throw new IOException("Failed to read motor database: " + dbCopy, e);
		}

		ThrustCurveMotorSetDatabase database = new ThrustCurveMotorSetDatabase();
		for (ThrustCurveMotor motor : motors) {
			database.addMotor(motor);
		}
		return database;
	}

	private static Path findCoreModuleRoot() {
		Path cwd = Path.of("").toAbsolutePath().normalize();

		if (Files.isDirectory(cwd.resolve("src/main/resources/datafiles/examples"))) {
			return cwd;
		}
		if (Files.isDirectory(cwd.resolve("core/src/main/resources/datafiles/examples"))) {
			return cwd.resolve("core");
		}
		throw new IllegalStateException("Unable to locate core module root from working directory: " + cwd);
	}
}

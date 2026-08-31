package info.openrocket.core.montecarlo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.simulation.montecarlo.LandingBodyFailure;
import info.openrocket.core.simulation.montecarlo.LandingPoint;
import info.openrocket.core.simulation.montecarlo.MonteCarloBranchResult;
import info.openrocket.core.simulation.montecarlo.MonteCarloDistribution;
import info.openrocket.core.simulation.montecarlo.MonteCarloMetric;
import info.openrocket.core.simulation.montecarlo.MonteCarloParameter;
import info.openrocket.core.simulation.montecarlo.MonteCarloResult;
import info.openrocket.core.simulation.montecarlo.MonteCarloRunResult;
import info.openrocket.core.simulation.montecarlo.MonteCarloSettings;
import info.openrocket.core.simulation.montecarlo.MonteCarloSimulationRunner;

/**
 * Compatibility facade used by the simulation-extension UI. Trajectories are
 * delegated to the deterministic upstream runner and every branch is retained.
 */
public final class MonteCarloBatchRunner {
    private MonteCarloBatchRunner() { }

    public interface ProgressCallback {
        void onProgress(int completed, int total);
    }

    public interface RecordConsumer {
        void onRecord(MonteCarloRunRecord record);
    }

    public static List<MonteCarloRunRecord> runBatch(Simulation simulation, int runs,
            ProgressCallback callback) {
        return runBatchParallel(simulation, runs, 1, callback);
    }

    public static List<MonteCarloRunRecord> runBatchParallel(Simulation simulation, int runs,
            int threads, ProgressCallback callback) {
		Objects.requireNonNull(simulation, "simulation");
		SimulationOptions batchOptions = simulation.getOptions().clone();
        MonteCarloResult result = runAnalysis(simulation, runs, threads, callback);
		return toLegacyRecords(simulation, result, batchOptions);
    }

    public static void runBatchParallelStreaming(Simulation simulation, int runs, int threads,
            ProgressCallback callback, RecordConsumer consumer) {
        for (MonteCarloRunRecord record : runBatchParallel(simulation, runs, threads, callback)) {
            if (consumer != null) consumer.onRecord(record);
        }
    }

    public static MonteCarloResult runAnalysis(Simulation simulation, int runs, int threads,
            ProgressCallback callback) {
        Objects.requireNonNull(simulation, "simulation");
        MonteCarloSettings settings = buildSettings(findMonteCarloExtension(simulation), runs, threads);
		return runAnalysis(simulation, settings, callback);
	}

	public static MonteCarloResult runAnalysis(Simulation simulation, MonteCarloSettings settings,
			ProgressCallback callback) {
		Objects.requireNonNull(simulation, "simulation");
		Objects.requireNonNull(settings, "settings");
        return new MonteCarloSimulationRunner().run(simulation, settings, (completed, total) -> {
            if (callback != null) callback.onProgress(completed, total);
        });
    }

    public static MonteCarloSettings buildSettings(MonteCarloExtension extension, int runs, int threads) {
        if (runs < MonteCarloSettings.MIN_RUN_COUNT) {
            throw new IllegalArgumentException("Monte Carlo requires at least "
                    + MonteCarloSettings.MIN_RUN_COUNT + " dispersed runs");
        }
        int seed = extension != null && extension.isUseDeterministicSeed()
                ? foldSeed(extension.getRandomSeed()) : ThreadLocalRandom.current().nextInt();
        MonteCarloSettings.Builder builder = MonteCarloSettings.builder()
                .runCount(runs).threadCount(Math.max(1, threads)).seed(seed);
        if (extension == null) return builder.build();

        add(builder, extension, MonteCarloParameter.WIND_SPEED, extension.getWindSpeedAverageSigmaMps());
        add(builder, extension, MonteCarloParameter.WIND_DIRECTION,
                Math.toRadians(extension.getWindDirectionStdDevDeg()));
        add(builder, extension, MonteCarloParameter.WIND_TURBULENCE,
                extension.getWindSpeedTurbulenceSigmaMps());
        add(builder, extension, MonteCarloParameter.AIR_DENSITY, extension.getDensityMultiplierSigma());
        add(builder, extension, MonteCarloParameter.AIR_TEMPERATURE, extension.getTemperatureStdDevC());
        add(builder, extension, MonteCarloParameter.AIR_PRESSURE,
                extension.getPressureStdDevMbar() * 100.0);
        add(builder, extension, MonteCarloParameter.LAUNCH_GUIDE_ANGLE,
                Math.toRadians(extension.getLaunchRodAngleStdDevDeg()));
        add(builder, extension, MonteCarloParameter.LAUNCH_GUIDE_DIRECTION,
                Math.toRadians(extension.getLaunchRodDirectionStdDevDeg()));
        add(builder, extension, MonteCarloParameter.LAUNCH_LATITUDE,
                extension.getLaunchLatitudeStdDevDeg());
        add(builder, extension, MonteCarloParameter.LAUNCH_LONGITUDE,
                extension.getLaunchLongitudeStdDevDeg());
        add(builder, extension, MonteCarloParameter.LAUNCH_ALTITUDE,
                extension.getLaunchAltitudeStdDevM());
        add(builder, extension, MonteCarloParameter.TOTAL_MASS, extension.getMassMultiplierSigma());
        add(builder, extension, MonteCarloParameter.CG_AXIAL, extension.getCgAxialSigmaM());
        add(builder, extension, MonteCarloParameter.AXIAL_DRAG, extension.getCdMultiplierSigma());
        add(builder, extension, MonteCarloParameter.NORMAL_FORCE,
                extension.getNormalForceMultiplierSigma());
        add(builder, extension, MonteCarloParameter.THRUST, extension.getThrustMultiplierSigma());
        add(builder, extension, MonteCarloParameter.IGNITION_DELAY,
                extension.getIgnitionDelaySigmaS());
        add(builder, extension, MonteCarloParameter.RECOVERY_DRAG,
                extension.getRecoveryDragMultiplierSigma());
        add(builder, extension, MonteCarloParameter.DEPLOYMENT_DELAY,
                extension.getDeploymentDelaySigmaS());
        return builder.build();
    }

    private static void add(MonteCarloSettings.Builder builder, MonteCarloExtension extension,
            MonteCarloParameter parameter, double spread) {
        if (spread > 0) {
            builder.uncertainty(parameter, MonteCarloDistribution.NORMAL, spread);
        }
    }

    private static int foldSeed(long seed) {
        return (int) (seed ^ (seed >>> 32));
    }

    private static MonteCarloExtension findMonteCarloExtension(Simulation simulation) {
        for (SimulationExtension extension : simulation.getSimulationExtensions()) {
            if (extension instanceof MonteCarloExtension monteCarlo) return monteCarlo;
        }
        return null;
    }

	public static List<MonteCarloRunRecord> toLegacyRecords(Simulation source, MonteCarloResult result) {
		return toLegacyRecords(source, result, source.getOptions().clone());
	}

	public static List<MonteCarloRunRecord> toLegacyRecords(Simulation source, MonteCarloResult result,
			SimulationOptions batchOptions) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(batchOptions, "batchOptions");
        List<MonteCarloRunRecord> records = new ArrayList<>(result.getRunResults().size() + 1);
        MonteCarloExtension extension = findMonteCarloExtension(source);
		records.add(toLegacyRecord(source, batchOptions, extension, result.getNominalResult(), true,
				result.getSettings().getSeed()));
        for (MonteCarloRunResult run : result.getRunResults()) {
			records.add(toLegacyRecord(source, batchOptions, extension, run, false,
					result.getSettings().getSeed()));
        }
        return records;
    }

    private static MonteCarloRunRecord toLegacyRecord(Simulation source, SimulationOptions batchOptions,
			MonteCarloExtension extension, MonteCarloRunResult run, boolean nominal, int masterSeed) {
        SimulationData data = new SimulationData();
        data.simulationName = source.getName();
        data.launchLat_deg = batchOptions.getLaunchLatitude();
        data.launchLon_deg = batchOptions.getLaunchLongitude();
        data.launchRodDirection_deg = Math.toDegrees(batchOptions.getLaunchRodDirection());
        data.apogee_m = run.maximumAltitude();
        data.flightTime_s = run.flightTime();
        data.hasApogee = Double.isFinite(run.maximumAltitude());

        MonteCarloBranchResult primaryBranch = run.branchResults().isEmpty() ? null : run.branchResults().get(0);
        if (primaryBranch != null) {
            data.maxVelocity_mps = primaryBranch.getMetric(MonteCarloMetric.MAXIMUM_VELOCITY);
            data.maxAcceleration_mps2 = primaryBranch.getMetric(MonteCarloMetric.MAXIMUM_ACCELERATION);
            data.apogeeTime_s = primaryBranch.getMetric(MonteCarloMetric.TIME_TO_APOGEE);
        }

        LandingPoint primary = run.landingPoints().isEmpty() ? null : run.landingPoints().get(0);
        if (primary != null && run.getFailureMessage(primary.bodyId()) == null) {
            populateLanding(data, primary.east(), primary.north());
        } else {
            data.hasLanding = false;
            data.landingEast_m = Double.NaN;
            data.landingNorth_m = Double.NaN;
            data.landingLat_deg = Double.NaN;
            data.landingLon_deg = Double.NaN;
        }

        boolean deterministic = extension != null && extension.isUseDeterministicSeed();
        double windSigma = extension == null ? 0 : extension.getWindSpeedAverageSigmaMps();
        double turbulenceSigma = extension == null ? 0 : extension.getWindSpeedTurbulenceSigmaMps();
        double cdSigma = extension == null ? 0 : extension.getCdMultiplierSigma();
        double thrustSigma = extension == null ? 0 : extension.getThrustMultiplierSigma();
        double massSigma = extension == null ? 0 : extension.getMassMultiplierSigma();

		GustShearMetrics gustMetrics = run.gustShearMetrics();
		RunWindDisturbanceProfile windProfile = run.windDisturbanceProfile();
		MonteCarloRunRecord record = new MonteCarloRunRecord(
                run.sample().getRunNumber(), source.getName(), deterministic,
                run.sample().getSimulationSeed(), windSigma, turbulenceSigma,
                extension != null && extension.isGustEventsEnabled(),
                extension != null && extension.isShearLayerEnabled(),
                extension == null ? 0 : extension.getGustEventCount(),
                extension == null ? 0 : extension.getGustWindowStartS(),
                extension == null ? 0 : extension.getGustWindowEndS(),
                extension == null ? 0 : extension.getGustDurationMeanS(),
                extension == null ? 0 : extension.getGustDurationSigmaS(),
                extension == null ? 0 : extension.getGustPeakDeltaMeanMps(),
                extension == null ? 0 : extension.getGustPeakDeltaSigmaMps(),
                extension == null ? 0 : extension.getShearCenterAltM(),
                extension == null ? 0 : extension.getShearThicknessM(),
                extension == null ? 0 : extension.getShearDeltaMeanMps(),
                extension == null ? 0 : extension.getShearDeltaSigmaMps(),
				gustMetrics != null ? gustMetrics.gustCount
						: windProfile != null ? windProfile.getGustCount() : 0,
				gustMetrics == null ? Double.NaN : gustMetrics.maxDeltaWind_mps,
				gustMetrics == null ? Double.NaN : gustMetrics.shearDelta_mps,
				gustMetrics == null ? Double.NaN : gustMetrics.deltaWindImpulse_mps_s,
				gustMetrics == null ? Double.NaN : gustMetrics.maxTilt_deg,
				gustMetrics == null ? Double.NaN : gustMetrics.maxAoA_deg,
                cdSigma, thrustSigma, massSigma,
                multiplier(run, MonteCarloParameter.AXIAL_DRAG),
                multiplier(run, MonteCarloParameter.THRUST),
                multiplier(run, MonteCarloParameter.TOTAL_MASS),
                batchOptions, data);
        record.nominal = nominal;
        record.simulationSeed = run.sample().getSimulationSeed();
		record.masterSeed = masterSeed;
        record.failureMessage = run.failureMessage();
		record.windDisturbanceSample = windProfile == null ? "" : windProfile.toAuditString();
        record.sampledVariations = run.sample().getVariations();
		record.uncertaintySettings = uncertaintySettings(extension);
        record.bodyResults = buildBodyResults(run, data.launchLat_deg, data.launchLon_deg);
        record.setLandingEastM(data.landingEast_m);
        record.setLandingNorthM(data.landingNorth_m);
        record.setLandingLatDeg(data.landingLat_deg);
        record.setLandingLonDeg(data.landingLon_deg);
        return record;
    }

	private static Map<MonteCarloParameter, String> uncertaintySettings(MonteCarloExtension extension) {
		if (extension == null) return Map.of();
		EnumMap<MonteCarloParameter, String> settings = new EnumMap<>(MonteCarloParameter.class);
		for (MonteCarloParameter parameter : MonteCarloParameter.values()) {
			double spread = switch (parameter) {
				case WIND_SPEED -> extension.getWindSpeedAverageSigmaMps();
				case WIND_DIRECTION -> Math.toRadians(extension.getWindDirectionStdDevDeg());
				case WIND_TURBULENCE -> extension.getWindSpeedTurbulenceSigmaMps();
				case AIR_DENSITY -> extension.getDensityMultiplierSigma();
				case AIR_TEMPERATURE -> extension.getTemperatureStdDevC();
				case AIR_PRESSURE -> extension.getPressureStdDevMbar() * 100.0;
				case LAUNCH_GUIDE_ANGLE -> Math.toRadians(extension.getLaunchRodAngleStdDevDeg());
				case LAUNCH_GUIDE_DIRECTION -> Math.toRadians(extension.getLaunchRodDirectionStdDevDeg());
				case LAUNCH_LATITUDE -> extension.getLaunchLatitudeStdDevDeg();
				case LAUNCH_LONGITUDE -> extension.getLaunchLongitudeStdDevDeg();
				case LAUNCH_ALTITUDE -> extension.getLaunchAltitudeStdDevM();
				case TOTAL_MASS -> extension.getMassMultiplierSigma();
				case CG_AXIAL -> extension.getCgAxialSigmaM();
				case AXIAL_DRAG -> extension.getCdMultiplierSigma();
				case NORMAL_FORCE -> extension.getNormalForceMultiplierSigma();
				case THRUST -> extension.getThrustMultiplierSigma();
				case IGNITION_DELAY -> extension.getIgnitionDelaySigmaS();
				case RECOVERY_DRAG -> extension.getRecoveryDragMultiplierSigma();
				case DEPLOYMENT_DELAY -> extension.getDeploymentDelaySigmaS();
			};
			settings.put(parameter, extension.getParameterDistribution(parameter).name() + ":" + spread);
		}
		return Map.copyOf(settings);
	}

    private static List<MonteCarloRunRecord.BodyResult> buildBodyResults(MonteCarloRunResult run,
            double launchLatitude, double launchLongitude) {
        Map<String, LandingPoint> points = new LinkedHashMap<>();
        for (LandingPoint point : run.landingPoints()) points.put(point.bodyId(), point);
        Map<String, LandingBodyFailure> failures = new LinkedHashMap<>();
        for (LandingBodyFailure failure : run.bodyFailures()) failures.put(failure.bodyId(), failure);
        Map<String, MonteCarloBranchResult> branches = new LinkedHashMap<>();
        for (MonteCarloBranchResult branch : run.branchResults()) branches.put(branch.branchId(), branch);

        LinkedHashMap<String, Boolean> identities = new LinkedHashMap<>();
        points.keySet().forEach(id -> identities.put(id, Boolean.TRUE));
        failures.keySet().forEach(id -> identities.put(id, Boolean.TRUE));
        branches.keySet().forEach(id -> identities.put(id, Boolean.TRUE));

        List<MonteCarloRunRecord.BodyResult> bodies = new ArrayList<>();
        for (String id : identities.keySet()) {
            LandingPoint point = points.get(id);
            LandingBodyFailure bodyFailure = failures.get(id);
            MonteCarloBranchResult branch = branches.get(id);
            int branchIndex = point != null ? point.branchIndex()
                    : bodyFailure != null ? bodyFailure.branchIndex()
                    : branch != null ? branch.branchIndex() : -1;
            String branchName = point != null ? point.branchName()
                    : bodyFailure != null ? bodyFailure.branchName()
                    : branch != null ? branch.branchName() : "";
            double east = point == null ? Double.NaN : point.east();
            double north = point == null ? Double.NaN : point.north();
            double[] latLon = point == null ? new double[] { Double.NaN, Double.NaN }
                    : LandingDispersion6DOF.enuToLatLonDeg(east, north, launchLatitude, launchLongitude);
            String failure = run.failureMessage() != null ? run.failureMessage()
                    : bodyFailure != null ? bodyFailure.message()
                    : branch != null ? branch.failureMessage() : null;
            Map<MonteCarloMetric, Double> metrics = branch == null
                    ? new EnumMap<>(MonteCarloMetric.class) : branch.metrics();
            bodies.add(new MonteCarloRunRecord.BodyResult(id, branchIndex, branchName,
                    point != null && failure == null, east, north, latLon[0], latLon[1],
                    failure, metrics));
        }
        return List.copyOf(bodies);
    }

    private static double multiplier(MonteCarloRunResult run, MonteCarloParameter parameter) {
        return 1.0 + run.sample().getVariation(parameter);
    }

    private static void populateLanding(SimulationData data, double east, double north) {
        data.hasLanding = true;
        data.landingEast_m = east;
        data.landingNorth_m = north;
        double[] latLon = LandingDispersion6DOF.enuToLatLonDeg(east, north,
                data.launchLat_deg, data.launchLon_deg);
        data.landingLat_deg = latLon[0];
        data.landingLon_deg = latLon[1];
    }
}

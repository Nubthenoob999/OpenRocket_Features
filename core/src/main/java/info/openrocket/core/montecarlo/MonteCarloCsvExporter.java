package info.openrocket.core.montecarlo;

import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.Chars;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import info.openrocket.core.simulation.montecarlo.MonteCarloMetric;
import info.openrocket.core.simulation.montecarlo.MonteCarloParameter;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;

/**
 * Writes Monte Carlo batch results into a "wide" CSV suitable for histogram/scatter plots.
 */
public final class MonteCarloCsvExporter {

    private static final double FEET_PER_M = 3.28084;

    // Cache units to avoid repeated lookup overhead in hot loops
    private static final Unit UNIT_TEMP_C = UnitGroup.UNITS_TEMPERATURE.getUnit(Chars.DEGREE + "C");
    private static final Unit UNIT_PRESSURE_MBAR = UnitGroup.UNITS_PRESSURE.getUnit("mbar");
    private static final Unit UNIT_VELOCITY_MPH = UnitGroup.UNITS_VELOCITY.getUnit("mph");
    private static final Unit UNIT_ANGLE_DEG = UnitGroup.UNITS_ANGLE.getUnit(String.valueOf(Chars.DEGREE));

    private MonteCarloCsvExporter() {}

    public static void exportDetailedCsv(File file, List<MonteCarloRunRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            throw new IOException("No records to export.");
        }

        // Determine max wind level count based on actual data
        int maxWindLevels = records.stream().mapToInt(r -> r.windLevels.size()).max().orElse(1);
        
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writeHeader(w, maxWindLevels);
            for (MonteCarloRunRecord r : records) {
                writeRow(w, r, maxWindLevels);
            }
        }
    }

	/** Write one row per flight-data branch/body, including failures and all metrics. */
	public static void exportBranchesCsv(File file, List<MonteCarloRunRecord> records) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			writer.write("run_index,nominal,simulation_seed,body_id,branch_index,branch_name,ground_hit,"
					+ "landing_east_m,landing_north_m,landing_lat_deg,landing_lon_deg,failure");
			for (MonteCarloMetric metric : MonteCarloMetric.values()) {
				writer.write("," + metric.name().toLowerCase());
			}
			writer.newLine();
			for (MonteCarloRunRecord record : records) {
				for (MonteCarloRunRecord.BodyResult body : record.bodyResults) {
					StringBuilder row = new StringBuilder();
					row.append(record.runIndex).append(',').append(record.nominal).append(',')
							.append(record.simulationSeed).append(',').append(csv(body.bodyId)).append(',')
							.append(body.branchIndex).append(',').append(csv(body.branchName)).append(',')
							.append(body.groundHit).append(',').append(body.eastM).append(',')
							.append(body.northM).append(',').append(body.latitudeDeg).append(',')
							.append(body.longitudeDeg).append(',').append(csv(body.failureMessage));
					for (MonteCarloMetric metric : MonteCarloMetric.values()) {
						row.append(',').append(body.metrics.getOrDefault(metric, Double.NaN));
					}
					writer.write(row.toString());
					writer.newLine();
				}
			}
		}
	}

    // --- Internal Helpers ---

    private static void writeHeader(BufferedWriter w, int maxWindLevels) throws IOException {
        StringBuilder header = new StringBuilder();
		header.append("run_index,nominal,simulation_name,deterministic_seed,master_seed,seed_used,simulation_seed,failure,wind_disturbance_sample,")
              .append("launch_lat_deg,launch_lon_deg,launch_alt_m,")
              .append("launch_rod_angle_deg,launch_rod_direction_deg,")
              .append("temperature_C,pressure_mbar,")
              .append("wind_model_type,wind_levels_used,")
              .append("wind_speed_avg_sigma_mps,wind_speed_avg_sigma_mph,")
              .append("wind_speed_turb_sigma_mps,wind_speed_turb_sigma_mph,");

        header.append("gust_enabled,shear_enabled,")
              .append("gust_count_cfg,gust_window_start_s,gust_window_end_s,")
              .append("gust_dur_mean_s,gust_dur_sigma_s,")
              .append("gust_peak_mean_mps,gust_peak_sigma_mps,")
              .append("shear_center_alt_m,shear_thickness_m,")
              .append("shear_delta_mean_mps,shear_delta_sigma_mps,")
              .append("gust_count_real,max_delta_wind_mps,max_delta_wind_mph,")
              .append("shear_delta_mps,shear_delta_mph,")
              .append("delta_wind_impulse_mps_s,max_tilt_deg,max_aoa_deg,")
              .append("cd_mult_sigma,thrust_mult_sigma,mass_mult_sigma,")
              .append("cd_mult_used,thrust_mult_used,mass_mult_used,")
              .append("table_valid,table_total_queries,table_successful_queries,table_interpolated_queries,")
              .append("table_reynolds_corrected_queries,table_fallback_count,table_failure_counts,")
              .append("table_runtime_flags,table_first_failure_reason,table_first_failure_time_s,")
              .append("table_first_failure_mach,table_first_failure_alpha_rad,table_first_failure_beta_rad,")
              .append("table_first_failure_powered_fraction,table_first_failure_reynolds,table_first_failure_detail,");

		for (MonteCarloParameter parameter : MonteCarloParameter.values()) {
			header.append("setting_").append(parameter.name().toLowerCase()).append(',')
					.append("sample_").append(parameter.name().toLowerCase()).append(',');
		}

        for (int i = 0; i < maxWindLevels; i++) {
            int n = i + 1;
            header.append("wind_level_").append(n).append("_alt_m,")
                  .append("wind_level_").append(n).append("_speed_mps,")
                  .append("wind_level_").append(n).append("_speed_mph,")
                  .append("wind_level_").append(n).append("_dir_deg,")
                  .append("wind_level_").append(n).append("_std_mps,")
                  .append("wind_level_").append(n).append("_turb_intensity,");
        }

        header.append("apogee_m,apogee_ft,apogee_time_s,landing_time_s,")
              .append("landing_east_m,landing_north_m,landing_lat_deg,landing_lon_deg,")
              .append("landing_downrange_m,landing_crossrange_m,has_apogee,has_landing\n");

        w.write(header.toString());
    }

    private static void writeRow(BufferedWriter w, MonteCarloRunRecord r, int maxWindLevels) throws IOException {
        SimulationData d = r.results;
        StringBuilder row = new StringBuilder(512);

        row.append(r.runIndex).append(",")
		   .append(r.nominal).append(",")
           .append(csv(safe(r.simulationName))).append(",")
           .append(r.deterministicSeed).append(",")
		   .append(r.masterSeed).append(",")
           .append(r.seedUsed).append(",")
		   .append(r.simulationSeed).append(",")
		   .append(csv(r.failureMessage)).append(",")
		   .append(csv(r.windDisturbanceSample)).append(",")

           .append(r.launchLatitudeDeg).append(",")
           .append(r.launchLongitudeDeg).append(",")
           .append(r.launchAltitudeM).append(",")

           .append(Math.toDegrees(r.launchRodAngleRad)).append(",")
           .append(Math.toDegrees(r.launchRodDirectionRad)).append(",")

           .append(UNIT_TEMP_C.toUnit(r.launchTemperatureK)).append(",")
           .append(UNIT_PRESSURE_MBAR.toUnit(r.launchPressurePa)).append(",")

           .append(csv(safe(r.windModelType))).append(",")
           .append(r.windLevels.size()).append(",")

           .append(r.windSpeedAverageSigmaMps).append(",")
           .append(UNIT_VELOCITY_MPH.toUnit(r.windSpeedAverageSigmaMps)).append(",")
           .append(r.windSpeedTurbulenceSigmaMps).append(",")
           .append(UNIT_VELOCITY_MPH.toUnit(r.windSpeedTurbulenceSigmaMps)).append(",");

        // Gust/shear config + metrics
        row.append(r.gustEventsEnabled).append(",")
           .append(r.shearLayerEnabled).append(",")
           .append(r.gustEventCountConfigured).append(",")
           .append(r.gustWindowStart_s).append(",")
           .append(r.gustWindowEnd_s).append(",")
           .append(r.gustDurationMean_s).append(",")
           .append(r.gustDurationSigma_s).append(",")
           .append(r.gustPeakDeltaMean_mps).append(",")
           .append(r.gustPeakDeltaSigma_mps).append(",")
           .append(r.shearCenterAlt_m).append(",")
           .append(r.shearThickness_m).append(",")
           .append(r.shearDeltaMean_mps).append(",")
           .append(r.shearDeltaSigma_mps).append(",")
           .append(r.gustEventCountRealized).append(",")
           .append(r.gustMaxDeltaWind_mps).append(",")
           .append(UNIT_VELOCITY_MPH.toUnit(r.gustMaxDeltaWind_mps)).append(",")
           .append(r.shearDeltaApplied_mps).append(",")
           .append(UNIT_VELOCITY_MPH.toUnit(r.shearDeltaApplied_mps)).append(",")
           .append(r.deltaWindImpulse_mps_s).append(",")
           .append(r.maxTilt_deg).append(",")
           .append(r.maxAoA_deg).append(",")
           .append(r.cdMultiplierSigma).append(",")
           .append(r.thrustMultiplierSigma).append(",")
           .append(r.massMultiplierSigma).append(",")
           .append(r.cdMultiplierUsed).append(",")
           .append(r.thrustMultiplierUsed).append(",")
           .append(r.massMultiplierUsed).append(",");

		appendTableRuntime(row, r.physicsAeroRuntimeReport);

		for (MonteCarloParameter parameter : MonteCarloParameter.values()) {
			row.append(csv(r.uncertaintySettings.get(parameter))).append(',')
					.append(r.sampledVariations.getOrDefault(parameter, 0.0)).append(',');
		}

        for (int i = 0; i < maxWindLevels; i++) {
            if (i < r.windLevels.size()) {
                MonteCarloRunRecord.WindLevel wl = r.windLevels.get(i);
                double mph = UNIT_VELOCITY_MPH.toUnit(wl.speedMps);
                double dirDeg = UNIT_ANGLE_DEG.toUnit(wl.directionRad);

                row.append(wl.altitudeM).append(",")
                   .append(wl.speedMps).append(",")
                   .append(mph).append(",")
                   .append(dirDeg).append(",")
                   .append(wl.stdDevMps).append(",")
                   .append(wl.turbIntensity).append(",");
            } else {
                row.append(",,,,,,"); // 6 columns
            }
        }

        row.append(d.apogee_m).append(",")
           .append(d.apogee_m * FEET_PER_M).append(",")
           .append(d.apogeeTime_s).append(",")
           .append(d.landingTime_s).append(",")
           .append(d.landingEast_m).append(",")
           .append(d.landingNorth_m).append(",")
           .append(d.landingLat_deg).append(",")
           .append(d.landingLon_deg).append(",")
           .append(d.landingDownrange_m).append(",")
           .append(d.landingCrossrange_m).append(",")
           .append(d.hasApogee).append(",")
           .append(d.hasLanding);

        row.append("\n"); // explicit newline
        w.write(row.toString());
    }

	private static void appendTableRuntime(StringBuilder row, PhysicsAeroRuntimeReport report) {
		PhysicsAeroRuntimeReport r = report == null ? PhysicsAeroRuntimeReport.disabled() : report;
		row.append(r.tableValid()).append(',').append(r.totalQueries()).append(',')
				.append(r.successfulTableQueries()).append(',').append(r.interpolatedQueries()).append(',')
				.append(r.reynoldsCorrectedQueries()).append(',').append(r.fallbackCount()).append(',')
				.append(csv(r.failureCounts().toString())).append(',')
				.append(csv(r.runtimeFlags().toString())).append(',');
		if (r.firstOccurrences().isEmpty()) {
			row.append(",,,,,,,,");
			return;
		}
		var entry = r.firstOccurrences().entrySet().iterator().next();
		var occurrence = entry.getValue();
		var coordinates = occurrence.coordinates();
		row.append(entry.getKey().name()).append(',')
				.append(occurrence.simulationTimeSeconds()).append(',')
				.append(coordinates.mach()).append(',').append(coordinates.alphaRad()).append(',')
				.append(coordinates.betaRad()).append(',').append(coordinates.poweredFraction()).append(',')
				.append(coordinates.reynoldsNumber()).append(',').append(csv(occurrence.detail())).append(',');
	}

    private static String csv(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String out = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + out + "\"" : out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

}

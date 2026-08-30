package info.openrocket.swing.simulation.extension.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.LandingDispersion6DOF;
import info.openrocket.core.montecarlo.MonteCarloBatchRunner;
import info.openrocket.core.montecarlo.MonteCarloCsvExporter;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

public class MonteCarloExportIntegrationTest extends BaseTestCase {
    @Test
    public void testNominalAndDispersedResultsExportToAllFormats(@TempDir Path directory)
            throws Exception {
        Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());
        simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
        simulation.getOptions().setLaunchRodLength(3.0);
        simulation.getOptions().getAverageWindModel().setAverage(2.0);

        MonteCarloExtension extension = new MonteCarloExtension();
        extension.setUseDeterministicSeed(true);
        extension.setRandomSeed(12345);
        extension.setCdMultiplierSigma(0.05);
        simulation.getSimulationExtensions().add(extension);

        List<MonteCarloRunRecord> records =
                MonteCarloBatchRunner.runBatchParallel(simulation, 2, 2, null);
        assertEquals(3, records.size());
        assertTrue(records.get(0).nominal);

        Path detailed = directory.resolve("analysis_detailed.csv");
        Path branches = directory.resolve("analysis_branches.csv");
        MonteCarloCsvExporter.exportDetailedCsv(detailed.toFile(), records);
        MonteCarloCsvExporter.exportBranchesCsv(branches.toFile(), records);
        LandingDispersion6DOF.exportAll(directory, "analysis_landing", records,
                simulation.getOptions().getLaunchLatitude(),
                simulation.getOptions().getLaunchLongitude());
        Path chart = directory.resolve("analysis_landing.png");
        Path kml = directory.resolve("analysis_landing.kml");
        Path points = directory.resolve("analysis_landing_points.csv");
        Path summary = directory.resolve("analysis_landing_summary.csv");
        Path report = directory.resolve("analysis_report.pdf");
        MonteCarloPdfExporter.export(report.toFile(), "Integration", records, chart.toFile());

        List<String> detailedLines = Files.readAllLines(detailed);
        assertEquals(4, detailedLines.size(), "header + nominal + two dispersed records");
        assertTrue(detailedLines.get(0).startsWith("run_index,nominal"));
        assertTrue(detailedLines.get(0).contains("master_seed"));
        assertTrue(detailedLines.get(0).contains("simulation_seed"));
        assertTrue(detailedLines.get(0).contains("failure"));
        assertTrue(detailedLines.get(0).contains("setting_total_mass"));
        assertTrue(detailedLines.get(0).contains("sample_total_mass"));
        assertTrue(detailedLines.get(1).startsWith("0,true,"),
                "the nominal reference must remain the first exported record");
        assertTrue(detailedLines.get(2).startsWith("1,false,"));
        assertTrue(detailedLines.get(3).startsWith("2,false,"));

        List<String> branchLines = Files.readAllLines(branches);
        assertTrue(branchLines.size() >= 4, "each run must retain at least one branch");
        assertTrue(branchLines.get(0).contains("body_id"));
        assertTrue(branchLines.get(0).contains("branch_index"));
        assertTrue(branchLines.stream().skip(1).anyMatch(line -> line.startsWith("0,true,")));
        assertTrue(branchLines.stream().skip(1).anyMatch(line -> line.startsWith("1,false,")));
        assertTrue(branchLines.stream().skip(1).anyMatch(line -> line.startsWith("2,false,")));

        List<String> pointLines = Files.readAllLines(points);
        assertEquals(3, pointLines.size(), "nominal reference must not pollute dispersion points");
        assertTrue(pointLines.get(1).startsWith("1,"));
        assertTrue(pointLines.get(2).startsWith("2,"));

        String summaryText = Files.readString(summary);
        assertTrue(summaryText.startsWith("n,launch_lat_deg"));
        assertTrue(summaryText.contains("empirical_r50_m"));
        assertTrue(summaryText.contains("empirical_r90_m"));
        assertTrue(summaryText.contains("empirical_r95_m"));
        assertTrue(summaryText.lines().skip(1).findFirst().orElseThrow().startsWith("2,"));

        String kmlText = Files.readString(kml);
        assertTrue(kmlText.contains("<kml"));
        assertTrue(kmlText.contains("<Placemark>"));
        assertFalse(kmlText.contains("NaN"));
        assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a },
                java.util.Arrays.copyOf(Files.readAllBytes(chart), 8));
        assertEquals("%PDF-", new String(Files.readAllBytes(report), 0, 5,
                java.nio.charset.StandardCharsets.US_ASCII));

        try (var files = Files.list(directory)) {
            List<Path> bodyFiles = files
                    .filter(path -> path.getFileName().toString().startsWith("analysis_landing_body_"))
                    .toList();
            assertEquals(4, bodyFiles.size(), "one body's points, summary, KML, and PNG bundle");
            assertTrue(bodyFiles.stream().allMatch(path -> {
                try {
                    return Files.size(path) > 0;
                } catch (java.io.IOException exception) {
                    return false;
                }
            }));
        }
    }

    @Test
    public void testMultiStageExportKeepsSeparatedLandingBodiesDistinct(@TempDir Path directory)
            throws Exception {
        Rocket rocket = TestRockets.makeMultiStageEventTestRocket();
        rocket.getSelectedConfiguration().setAllStages();
        Simulation simulation = new Simulation(rocket);
        simulation.setFlightConfigurationId(
                rocket.getSelectedConfiguration().getFlightConfigurationID());
        simulation.getOptions().setISAAtmosphere(true);
        simulation.getOptions().setTimeStep(0.05);
        simulation.getOptions().getAverageWindModel().setAverage(0.1);

        MonteCarloExtension extension = new MonteCarloExtension();
        extension.setUseDeterministicSeed(true);
        extension.setRandomSeed(12345);
        simulation.getSimulationExtensions().add(extension);

        List<MonteCarloRunRecord> records =
                MonteCarloBatchRunner.runBatchParallel(simulation, 2, 2, null);
        LandingDispersion6DOF.exportAll(directory, "multistage", records,
                simulation.getOptions().getLaunchLatitude(),
                simulation.getOptions().getLaunchLongitude());

        List<String> bodyKmlFiles;
        try (var files = Files.list(directory)) {
            bodyKmlFiles = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("multistage_body_") && name.endsWith(".kml"))
                    .toList();
        }
        assertTrue(bodyKmlFiles.stream().anyMatch(name -> name.contains("Center_Booster")),
                "the center booster must have its own landing export: " + bodyKmlFiles);
        assertTrue(bodyKmlFiles.stream().anyMatch(name -> name.contains("Side_boosters")),
                "the side boosters must have their own landing export: " + bodyKmlFiles);
        assertTrue(bodyKmlFiles.size() >= 2,
                "independently landed bodies must not collapse into one cloud: "
                        + bodyKmlFiles);
    }
}

package info.openrocket.core.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.montecarlo.MonteCarloResult;
import info.openrocket.core.simulation.montecarlo.MonteCarloDistribution;
import info.openrocket.core.simulation.montecarlo.MonteCarloParameter;
import info.openrocket.core.simulation.montecarlo.MonteCarloSettings;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class MonteCarloBatchRunnerTest extends BaseTestCase {
    @Test
    public void testExtensionSettingsForceNormalSamplerContract() {
        MonteCarloExtension extension = new MonteCarloExtension();
        extension.setUseDeterministicSeed(true);
        extension.setRandomSeed(0x1234_5678_9abcl);
        extension.setWindDirectionStdDevDeg(10);
        extension.setPressureStdDevMbar(12);
        extension.setMassMultiplierSigma(0.04);
        extension.setRecoveryDragMultiplierSigma(0.15);
        extension.setDeploymentDelaySigmaS(0.25);
        extension.setParameterDistribution(MonteCarloParameter.WIND_DIRECTION,
                MonteCarloDistribution.UNIFORM);
        extension.setParameterDistribution(MonteCarloParameter.TOTAL_MASS,
                MonteCarloDistribution.LOG_NORMAL);

        MonteCarloSettings settings = MonteCarloBatchRunner.buildSettings(extension, 25, 3);

        assertEquals(25, settings.getRunCount());
        assertEquals(3, settings.getThreadCount());
        assertEquals(Math.toRadians(10),
                settings.getUncertainty(MonteCarloParameter.WIND_DIRECTION).spread(), 1e-12);
        assertEquals(MonteCarloDistribution.NORMAL,
                settings.getUncertainty(MonteCarloParameter.WIND_DIRECTION).distribution());
        assertEquals(1200, settings.getUncertainty(MonteCarloParameter.AIR_PRESSURE).spread());
        assertEquals(0.04, settings.getUncertainty(MonteCarloParameter.TOTAL_MASS).spread());
        assertEquals(MonteCarloDistribution.NORMAL,
                settings.getUncertainty(MonteCarloParameter.TOTAL_MASS).distribution());
        assertEquals(0.15, settings.getUncertainty(MonteCarloParameter.RECOVERY_DRAG).spread());
        assertEquals(0.25, settings.getUncertainty(MonteCarloParameter.DEPLOYMENT_DELAY).spread());
        for (MonteCarloParameter parameter : MonteCarloParameter.values()) {
            assertEquals(MonteCarloDistribution.NORMAL,
                    extension.getParameterDistribution(parameter));
        }
    }

    @Test
    public void testAuxiliaryGustsAreSeededPerRunAndExcludedFromNominalReference() {
        Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());
        simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
        simulation.getOptions().setLaunchRodLength(3.0);

        MonteCarloExtension extension = new MonteCarloExtension();
        extension.setUseDeterministicSeed(true);
        extension.setRandomSeed(0x5eed);
        extension.setGustEventsEnabled(true);
        extension.setGustEventCount(2);
        extension.setGustWindowStartS(0.5);
        extension.setGustWindowEndS(3.0);
        extension.setGustDurationMeanS(0.4);
        extension.setGustDurationSigmaS(0.05);
        extension.setGustPeakDeltaMeanMps(2.0);
        extension.setGustPeakDeltaSigmaMps(0.1);
        simulation.getSimulationExtensions().add(extension);

        MonteCarloResult result = MonteCarloBatchRunner.runAnalysis(simulation, 2, 2, null);

        assertNull(result.getNominalResult().gustShearMetrics());
        assertNull(result.getNominalResult().windDisturbanceProfile());
        assertEquals(2, result.getRunResults().size());
        result.getRunResults().forEach(run -> {
            assertNotNull(run.gustShearMetrics());
            assertNotNull(run.windDisturbanceProfile());
            assertTrue(run.windDisturbanceProfile().getGustCount() > 0);
			assertTrue(run.gustShearMetrics().maxDeltaWind_mps > 0,
					"the query-time disturbance must reach the simulated flight");
        });
        assertTrue(result.getRunResults().get(0).sample().getSimulationSeed()
                != result.getRunResults().get(1).sample().getSimulationSeed());

		MonteCarloResult repeated = MonteCarloBatchRunner.runAnalysis(simulation, 2, 1, null);
		for (int index = 0; index < result.getRunResults().size(); index++) {
			assertEquals(result.getRunResults().get(index).windDisturbanceProfile().toAuditString(),
					repeated.getRunResults().get(index).windDisturbanceProfile().toAuditString());
		}
    }
}

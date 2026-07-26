package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RASAeroLoaderTest extends BaseTestCase {
    private static final double EPSILON = 0.0001;

    /**
     * Test loading a three stage RASAero rocket and verifying the parameters.
     */
    @Test
    public void testThreeStageRocket() {
        RASAeroLoader loader = new RASAeroLoader();
        InputStream stream = this.getClass().getResourceAsStream("/file/rasaero/importt/Three-stage rocket.CDX1");
        assertNotNull(stream, "Could not open Three-stage rocket.CDX1");
        try {
            OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
            DocumentLoadingContext context = new DocumentLoadingContext();
            context.setOpenRocketDocument(doc);
            context.setMotorFinder(new DatabaseMotorFinder());
            loader.loadFromStream(context, new BufferedInputStream(stream), "Three-stage rocket");
            Rocket rocket = doc.getRocket();
            assertNotNull(rocket);

            // Test number and type of components
            assertEquals(3, rocket.getChildCount(), "Incorrect amount of stages");
            AxialStage sustainer = rocket.getStage(0);
            AxialStage booster1 = rocket.getStage(1);
            AxialStage booster2 = rocket.getStage(2);

            assertEquals(2, sustainer.getChildCount(), "Incorrect amount of sustainer children");
            assertEquals(1, booster1.getChildCount(), "Incorrect amount of booster 1 children");
            assertEquals(1, booster2.getChildCount(), "Incorrect amount of booster 2 children");

            RocketComponent noseCone = sustainer.getChild(0);
            assertTrue(noseCone instanceof NoseCone, "First component should be nose cone");
            assertEquals(0, noseCone.getChildCount());
            RocketComponent bodyTube = sustainer.getChild(1);
            assertTrue(bodyTube instanceof BodyTube, "Second component should be body tube");
            assertEquals(1, bodyTube.getChildCount());
            RocketComponent finSet = bodyTube.getChild(0);
            assertTrue(finSet instanceof TrapezoidFinSet, "Body tube child should be trapezoid fin set");

            RocketComponent booster1Tube = booster1.getChild(0);
            assertTrue(booster1Tube instanceof BodyTube, "Booster child should be nose cone");
            assertEquals(1, booster1Tube.getChildCount());
            RocketComponent booster1FinSet = booster1Tube.getChild(0);
            assertTrue(booster1FinSet instanceof TrapezoidFinSet, "Booster 1 tube child should be trapezoid fin set");

            RocketComponent booster2Tube = booster2.getChild(0);
            assertTrue(booster2Tube instanceof BodyTube, "Booster child should be nose cone");
            assertEquals(1, booster2Tube.getChildCount());
            RocketComponent booster2FinSet = booster2Tube.getChild(0);
            assertTrue(booster2FinSet instanceof TrapezoidFinSet, "Booster 1 tube child should be trapezoid fin set");

            // Test component parameters
            assertEquals(rocket.getName(), "Three-stage rocket");

            //// Sustainer
            NoseCone nose = (NoseCone) noseCone;
            assertEquals(Transition.Shape.OGIVE, nose.getShapeType());
            assertEquals(0.0125, nose.getBaseRadius(), EPSILON);
            assertEquals(0.1, nose.getLength(), EPSILON);
            assertEquals(0.002, nose.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, nose.getFinish());

            BodyTube tube = (BodyTube) bodyTube;
            assertEquals(0.0125, tube.getOuterRadius(), EPSILON);
            assertTrue(tube.isOuterRadiusAutomatic());
            assertEquals(0.3, tube.getLength(), EPSILON);
            assertEquals(0.002, tube.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, tube.getFinish());

            TrapezoidFinSet fins = (TrapezoidFinSet) finSet;
            assertEquals(4, fins.getFinCount());
            assertEquals(0, fins.getCantAngle(), EPSILON);
            assertEquals(0.05, fins.getRootChord(), EPSILON);
            assertEquals(0.05, fins.getTipChord(), EPSILON);
            assertEquals(0.03, fins.getHeight(), EPSILON);
            assertEquals(0.6947, fins.getSweepAngle(), EPSILON);
            assertEquals(FinSet.CrossSection.SQUARE, fins.getCrossSection());
            assertEquals(0.00201, fins.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, fins.getFinish());

            //// Booster 1
            BodyTube tube1 = (BodyTube) booster1Tube;
            assertEquals(0.0125, tube1.getOuterRadius(), EPSILON);
            assertFalse(tube1.isOuterRadiusAutomatic());
            assertEquals(0.08, tube1.getLength(), EPSILON);
            assertEquals(0.002, tube1.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, tube1.getFinish());

            TrapezoidFinSet fins1 = (TrapezoidFinSet) booster1FinSet;
            assertEquals(4, fins1.getFinCount());
            assertEquals(0, fins1.getCantAngle(), EPSILON);
            assertEquals(0.08, fins1.getRootChord(), EPSILON);
            assertEquals(0.073, fins1.getTipChord(), EPSILON);
            assertEquals(0.03, fins1.getHeight(), EPSILON);
            assertEquals(0.6947, fins1.getSweepAngle(), EPSILON);
            assertEquals(FinSet.CrossSection.SQUARE, fins1.getCrossSection());
            assertEquals(0.00201, fins1.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, fins1.getFinish());

            //// Booster 2
            BodyTube tube2 = (BodyTube) booster2Tube;
            assertEquals(0.0125, tube2.getOuterRadius(), EPSILON);
            assertFalse(tube2.isOuterRadiusAutomatic());
            assertEquals(0.08, tube2.getLength(), EPSILON);
            assertEquals(0.002, tube2.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, tube2.getFinish());

            TrapezoidFinSet fins2 = (TrapezoidFinSet) booster2FinSet;
            assertEquals(4, fins2.getFinCount());
            assertEquals(0, fins2.getCantAngle(), EPSILON);
            assertEquals(0.08, fins2.getRootChord(), EPSILON);
            assertEquals(0.03, fins2.getTipChord(), EPSILON);
            assertEquals(0.04, fins2.getHeight(), EPSILON);
            assertEquals(0.5584, fins2.getSweepAngle(), EPSILON);
            assertEquals(FinSet.CrossSection.SQUARE, fins2.getCrossSection());
            assertEquals(0.00201, fins2.getThickness(), EPSILON);
            assertEquals(ExternalComponent.Finish.MIRROR, fins2.getFinish());
        } catch (IllegalStateException ise) {
            fail(ise.getMessage());
        } catch (RocketLoadException | IOException e) {
            throw new RuntimeException(e);
        }
        assertTrue(loader.getWarnings().isEmpty());
    }

    /**
     * Test whether we can load a very complex, unrealistic rocket with practically
     * all RASAero features.
     */
    @Test
    public void testShowRocket() {
        RASAeroLoader loader = new RASAeroLoader();
        InputStream stream = this.getClass().getResourceAsStream("/file/rasaero/importt/Show-off.CDX1");
        assertNotNull(stream, "Could not open Show-off.CDX1");
        try {
            OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
            DocumentLoadingContext context = new DocumentLoadingContext();
            context.setOpenRocketDocument(doc);
            context.setMotorFinder(new DatabaseMotorFinder());
            loader.loadFromStream(context, new BufferedInputStream(stream), null);
            Rocket rocket = doc.getRocket();
            assertNotNull(rocket);

            RocketComponent boattail = rocket.getAllChildren().stream()
                    .filter(component -> component instanceof Transition)
                    .filter(component -> "Boattail".equals(component.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Imported boattail missing"));
            assertTrue(boattail.getParent() instanceof AxialStage,
                    "Boattail must be in the continuous stage body chain");
            assertEquals(0.0254, boattail.getLength(), EPSILON);
            assertEquals(0.003175, ((Transition) boattail).getAftRadius(), EPSILON);
        } catch (IllegalStateException ise) {
            fail(ise.getMessage());
        } catch (RocketLoadException | IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals(5, loader.getWarnings().size());
    }

    @Test
    public void testSimulationNozzleDiametersAreImportedInMeters() throws IOException, RocketLoadException {
        InputStream resource = this.getClass().getResourceAsStream("/file/rasaero/importt/Show-off.CDX1");
        assertNotNull(resource, "Could not open Show-off.CDX1");
        String cdx1 = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                .replaceFirst("<SustainerNozzleDiameter>0</SustainerNozzleDiameter>",
                        "<SustainerNozzleDiameter>1.25</SustainerNozzleDiameter>")
                .replaceFirst("<Booster1NozzleDiameter>0</Booster1NozzleDiameter>",
                        "<Booster1NozzleDiameter>0.75</Booster1NozzleDiameter>")
                .replaceFirst("<Booster2NozzleDiameter>0</Booster2NozzleDiameter>",
                        "<Booster2NozzleDiameter>0.5</Booster2NozzleDiameter>");

        RASAeroLoader loader = new RASAeroLoader();
        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());
        loader.loadFromStream(context,
                new BufferedInputStream(new ByteArrayInputStream(cdx1.getBytes(StandardCharsets.UTF_8))), null);

        assertFalse(doc.getSimulations().isEmpty());
        var options = doc.getSimulations().get(0).getOptions();
        assertEquals(1.25 * 0.0254, options.getNozzleExitDiameterForStage(0), EPSILON);
        assertEquals(0.75 * 0.0254, options.getNozzleExitDiameterForStage(1), EPSILON);
        assertEquals(0.50 * 0.0254, options.getNozzleExitDiameterForStage(2), EPSILON);
    }

    @Test
    public void testTurbulenceTrueForcesFullyTurbulentBoundaryLayer()
            throws IOException, RocketLoadException {
        InputStream resource = this.getClass().getResourceAsStream(
                "/file/rasaero/importt/Show-off.CDX1");
        assertNotNull(resource);
        String cdx1 = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                .replaceFirst("<Turbulence>False</Turbulence>",
                        "<Turbulence>True</Turbulence>");

        RASAeroLoader loader = new RASAeroLoader();
        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());
        loader.loadFromStream(context,
                new BufferedInputStream(new ByteArrayInputStream(
                        cdx1.getBytes(StandardCharsets.UTF_8))), null);

        assertFalse(doc.getSimulations().isEmpty());
        assertTrue(doc.getSimulations().stream().allMatch(simulation ->
                simulation.getOptions().isForceTurbulentBoundaryLayer()));
        assertTrue(loader.getWarnings().stream().anyMatch(warning ->
                warning.toString().contains("fully turbulent boundary layer")));
    }

    @Test
    public void testBlankPressureUsesElevationPressureAndPreservesCdxTemperature()
            throws IOException, RocketLoadException {
        InputStream resource = this.getClass().getResourceAsStream(
                "/file/rasaero/importt/Show-off.CDX1");
        assertNotNull(resource);
        String cdx1 = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                .replaceFirst("<Altitude>410</Altitude>", "<Altitude>3750</Altitude>")
                .replaceFirst("<Pressure>2</Pressure>", "<Pressure>0</Pressure>")
                .replaceFirst("<Temperature>74</Temperature>",
                        "<Temperature>80</Temperature>");

        RASAeroLoader loader = new RASAeroLoader();
        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());
        loader.loadFromStream(context,
                new BufferedInputStream(new ByteArrayInputStream(
                        cdx1.getBytes(StandardCharsets.UTF_8))), null);

        var options = doc.getSimulations().get(0).getOptions();
        double altitudeM = 3750 / 3.28084;
        assertFalse(options.isISAAtmosphere());
        assertEquals(altitudeM, options.getLaunchAltitude(), EPSILON);
        assertEquals((80 + 459.67) * 5.0 / 9.0,
                options.getLaunchTemperature(), EPSILON);
        assertEquals(new info.openrocket.core.models.atmosphere.ExtendedISAModel()
                        .getConditions(altitudeM).getPressure(),
                options.getLaunchPressure(), 0.1);
    }

    @Test
    public void testBarometricPressureIsConvertedFromSeaLevelToLaunchSite()
            throws IOException, RocketLoadException {
        InputStream resource = this.getClass().getResourceAsStream(
                "/file/rasaero/importt/Show-off.CDX1");
        assertNotNull(resource);
        String cdx1 = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                .replaceFirst("<Altitude>410</Altitude>", "<Altitude>3750</Altitude>")
                .replaceFirst("<Pressure>2</Pressure>", "<Pressure>30.10</Pressure>")
                .replaceFirst("<Temperature>74</Temperature>",
                        "<Temperature>80</Temperature>");

        RASAeroLoader loader = new RASAeroLoader();
        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());
        loader.loadFromStream(context,
                new BufferedInputStream(new ByteArrayInputStream(
                        cdx1.getBytes(StandardCharsets.UTF_8))), null);

        var options = doc.getSimulations().get(0).getOptions();
        double altitudeM = 3750 / 3.28084;
        double standardSitePressure =
                new info.openrocket.core.models.atmosphere.ExtendedISAModel()
                        .getConditions(altitudeM).getPressure();
        double seaLevelPa = 30.10
                / info.openrocket.core.file.rasaero.RASAeroCommonConstants
                        .OPENROCKET_TO_RASAERO_PRESSURE;
        double expected = seaLevelPa * standardSitePressure
                / info.openrocket.core.models.atmosphere.ExtendedISAModel
                        .STANDARD_PRESSURE;
        assertEquals(expected, options.getLaunchPressure(), 0.1);
    }

    @Test
    public void testLaunchShoeAreaIsPreservedAsTwoSolidProtuberances()
            throws IOException, RocketLoadException {
        InputStream resource = this.getClass().getResourceAsStream(
                "/file/rasaero/importt/Three-stage rocket.CDX1");
        assertNotNull(resource);
        String cdx1 = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                .replaceFirst("<LaunchShoeArea>0</LaunchShoeArea>",
                        "<LaunchShoeArea>0.125</LaunchShoeArea>");

        RASAeroLoader loader = new RASAeroLoader();
        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());
        loader.loadFromStream(context,
                new BufferedInputStream(new ByteArrayInputStream(
                        cdx1.getBytes(StandardCharsets.UTF_8))), null);

        RailButton shoe = doc.getRocket().getAllChildren().stream()
                .filter(RailButton.class::isInstance)
                .map(RailButton.class::cast)
                .filter(component -> "Launch Shoe".equals(component.getName()))
                .findFirst().orElseThrow();
        assertEquals(2, shoe.getInstanceCount());
        assertEquals(0.125 * 0.0254 * 0.0254,
                shoe.getOuterDiameter() * shoe.getTotalHeight(), 1.0e-9);
        assertEquals(0, shoe.getInnerHeight(), 1.0e-12);
    }

    /**
     * Test a complex two-stage rocket with practically all RASAero features.
     */
    @Test
    public void testComplexTwoStageRocket() {
        RASAeroLoader loader = new RASAeroLoader();
        InputStream stream = this.getClass().getResourceAsStream("/file/rasaero/importt/Complex.Two-Stage.CDX1");
        assertNotNull(stream, "Could not open Complex.Two-Stage.CDX1");
        try {
            OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
            DocumentLoadingContext context = new DocumentLoadingContext();
            context.setOpenRocketDocument(doc);
            context.setMotorFinder(new DatabaseMotorFinder());
            loader.loadFromStream(context, new BufferedInputStream(stream), null);
            Rocket rocket = doc.getRocket();
            assertNotNull(rocket);

            // TODO: fetch components and test their parameters
        } catch (IllegalStateException ise) {
            fail(ise.getMessage());
        } catch (RocketLoadException | IOException e) {
            throw new RuntimeException(e);
        }
        // TODO: this also includes all motor warnings, so change motor db in setUp() to
        // include OR motors so the total
        // warning size decreases
        assertEquals(4, loader.getWarnings().size());
    }
}

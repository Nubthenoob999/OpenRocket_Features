package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.aerodynamics.rom.RomSurfaceHashUtil;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.util.BaseTestCase;

public class SimulationOptionsRomSurfaceTest extends BaseTestCase {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static DragSurface constantSurface(double cdOff, double cdOn) {
        double[] mach = new double[] { 0.0, 1.0 };
        double[] re = new double[] { 4.0, 8.0 };
        double[] alpha = new double[] { 0.0, 10.0 };
        double[][][] off = new double[mach.length][re.length][alpha.length];
        double[][][] on = new double[mach.length][re.length][alpha.length];
        for (int im = 0; im < mach.length; im++) {
            for (int ir = 0; ir < re.length; ir++) {
                for (int ia = 0; ia < alpha.length; ia++) {
                    off[im][ir][ia] = cdOff;
                    on[im][ir][ia] = cdOn;
                }
            }
        }
        return new DragSurface(mach, re, alpha, off, on, HASH, 0.0);
    }

    private static AeroSurface4D constantSurface4D(double cdOffBeta0, double cdOffBeta15,
            double cdOnBeta0, double cdOnBeta15) {
        double[] mach = new double[] { 0.0, 1.0 };
        double[] re = new double[] { 4.0, 8.0 };
        double[] alpha = new double[] { 0.0, 10.0 };
        double[] beta = new double[] { 0.0, 15.0 };

        double[][][][] off = new double[mach.length][re.length][alpha.length][beta.length];
        double[][][][] on = new double[mach.length][re.length][alpha.length][beta.length];
        double[][][][] body = new double[mach.length][re.length][alpha.length][beta.length];
        double[][][][] cn = new double[mach.length][re.length][alpha.length][beta.length];
        double[][][][] cm = new double[mach.length][re.length][alpha.length][beta.length];

        for (int im = 0; im < mach.length; im++) {
            for (int ir = 0; ir < re.length; ir++) {
                for (int ia = 0; ia < alpha.length; ia++) {
                    off[im][ir][ia][0] = cdOffBeta0;
                    off[im][ir][ia][1] = cdOffBeta15;
                    on[im][ir][ia][0] = cdOnBeta0;
                    on[im][ir][ia][1] = cdOnBeta15;
                    body[im][ir][ia][0] = Math.max(0.001, cdOffBeta0 - 0.03);
                    body[im][ir][ia][1] = Math.max(0.001, cdOffBeta15 - 0.03);
                }
            }
        }

        return new AeroSurface4D(mach, re, alpha, beta, off, on, body, cn, cm, HASH, 4);
    }

    @Test
    public void testRomSurfaceInstalledIntoSimulationConditions() {
        SimulationOptions options = new SimulationOptions();
        DragSurface surface = constantSurface(0.4, 0.35);
        options.setRomDragSurface(surface);

        SimulationConditions conditions = options.toSimulationConditions();
        assertNotNull(conditions.getRomAerodynamicCalculator());
        assertTrue(conditions.getRomAerodynamicCalculator().hasSurface());
        assertTrue(conditions.getAerodynamicCalculator() instanceof RomAerodynamicCalculator);
    }

    @Test
    public void testSimulationConditionsFallsBackToBarrowmanWhenNoSurface() {
        SimulationOptions options = new SimulationOptions();
        SimulationConditions conditions = options.toSimulationConditions();

        assertNotNull(conditions.getRomAerodynamicCalculator());
        assertFalse(conditions.getRomAerodynamicCalculator().hasSurface());
        assertTrue(conditions.getAerodynamicCalculator() instanceof BarrowmanCalculator);
    }

    @Test
    public void testCloneAndCopyConditionsPreserveRomSurfaceReference() {
        SimulationOptions source = new SimulationOptions();
        DragSurface surface = constantSurface(0.5, 0.2);
        source.setRomDragSurface(surface);

        SimulationOptions clone = source.clone();
        assertNotNull(clone);
        assertTrue(clone.hasRomDragSurface());
        assertSame(surface, clone.getRomDragSurface());

        SimulationOptions target = new SimulationOptions();
        target.copyConditionsFrom(source);
        assertTrue(target.hasRomDragSurface());
        assertSame(surface, target.getRomDragSurface());

        source.setRomDragSurface(null);
        target.copyConditionsFrom(source);
        assertFalse(target.hasRomDragSurface());
    }

    @Test
    public void testRomCalculatorInstalledSurfaceMatchesOptionsSurface() {
        SimulationOptions options = new SimulationOptions();
        DragSurface surface = constantSurface(0.37, 0.31);
        options.setRomDragSurface(surface);

        SimulationConditions conditions = options.toSimulationConditions();
        RomAerodynamicCalculator rom = conditions.getRomAerodynamicCalculator();
        assertNotNull(rom);
        assertTrue(rom.hasSurface());
        assertSame(surface, rom.getInstalledSurface());
        assertEquals(surface.geometryHash, rom.getInstalledSurface().geometryHash);
    }

    @Test
    public void testReplacingAndClearingRomSurfaceUpdatesCalculatorChoice() {
        SimulationOptions options = new SimulationOptions();
        DragSurface first = constantSurface(0.33, 0.27);
        DragSurface second = constantSurface(0.42, 0.36);

        options.setRomDragSurface(first);
        SimulationConditions firstConditions = options.toSimulationConditions();
        assertTrue(firstConditions.getAerodynamicCalculator() instanceof RomAerodynamicCalculator);
        assertSame(first, firstConditions.getRomAerodynamicCalculator().getInstalledSurface());

        options.setRomDragSurface(second);
        SimulationConditions secondConditions = options.toSimulationConditions();
        assertTrue(secondConditions.getAerodynamicCalculator() instanceof RomAerodynamicCalculator);
        assertSame(second, secondConditions.getRomAerodynamicCalculator().getInstalledSurface());

        options.setRomDragSurface(null);
        SimulationConditions clearedConditions = options.toSimulationConditions();
        assertTrue(clearedConditions.getAerodynamicCalculator() instanceof BarrowmanCalculator);
        assertFalse(clearedConditions.getRomAerodynamicCalculator().hasSurface());
    }

    @Test
    public void testRom4DSurfaceInstalledIntoSimulationConditions() {
        SimulationOptions options = new SimulationOptions();
        AeroSurface4D surface4D = constantSurface4D(0.42, 0.47, 0.35, 0.40);
        options.setRomSurfaceMode(RomSurfaceMode.FOUR_D);
        options.setRomAeroSurface4D(surface4D);

        SimulationConditions conditions = options.toSimulationConditions();
        RomAerodynamicCalculator rom = conditions.getRomAerodynamicCalculator();
        assertNotNull(rom);
        assertTrue(rom.hasSurface());
        assertTrue(conditions.getAerodynamicCalculator() instanceof RomAerodynamicCalculator);
        assertNotNull(rom.getInstalledSurface());
        assertTrue(RomSurfaceHashUtil.matchesGeometry(rom.getInstalledSurface().geometryHash, HASH));
    }

    @Test
    public void testCloneAndCopyConditionsPreserveRom4DSurfaceReference() {
        SimulationOptions source = new SimulationOptions();
        AeroSurface4D surface4D = constantSurface4D(0.41, 0.46, 0.34, 0.39);
        source.setRomSurfaceMode(RomSurfaceMode.FOUR_D);
        source.setRomAeroSurface4D(surface4D);

        SimulationOptions clone = source.clone();
        assertTrue(clone.hasRomAeroSurface4D());
        assertSame(surface4D, clone.getRomAeroSurface4D());
        assertEquals(RomSurfaceMode.FOUR_D, clone.getRomSurfaceMode());

        SimulationOptions target = new SimulationOptions();
        target.copyConditionsFrom(source);
        assertTrue(target.hasRomAeroSurface4D());
        assertSame(surface4D, target.getRomAeroSurface4D());
        assertEquals(RomSurfaceMode.FOUR_D, target.getRomSurfaceMode());

        source.setRomAeroSurface4D(null);
        target.copyConditionsFrom(source);
        assertFalse(target.hasRomAeroSurface4D());
    }

    @Test
    public void testSelectedRomSurfaceModeControlsInstalledSurface() {
        SimulationOptions options = new SimulationOptions();
        DragSurface surface3D = constantSurface(0.35, 0.30);
        AeroSurface4D surface4D = constantSurface4D(0.42, 0.47, 0.35, 0.40);
        options.setRomDragSurface(surface3D);
        options.setRomAeroSurface4D(surface4D);

        options.setRomSurfaceMode(RomSurfaceMode.THREE_D);
        SimulationConditions threeDConditions = options.toSimulationConditions();
        assertSame(surface3D, threeDConditions.getRomAerodynamicCalculator().getInstalledSurface());

        options.setRomSurfaceMode(RomSurfaceMode.FOUR_D);
        SimulationConditions fourDConditions = options.toSimulationConditions();
        assertTrue(RomSurfaceHashUtil.matchesGeometry(
                fourDConditions.getRomAerodynamicCalculator().getInstalledSurface().geometryHash, HASH));
    }
}

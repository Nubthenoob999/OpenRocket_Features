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
}

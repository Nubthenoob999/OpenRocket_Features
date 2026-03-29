package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

public class RomPrestepPanelTest extends BaseTestCase {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void testPanelShowsNotComputedWhenNoSurfaceInstalled() throws Exception {
        OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
        Simulation simulation = doc.getSimulations().get(0);

        RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));
        JLabel status = field(panel, "surfaceStatusValue", JLabel.class);
        assertEquals("Not computed", status.getText());
    }

    @Test
    public void testValidationWithoutSurfaceShowsExplicitMessage() throws Exception {
        OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
        Simulation simulation = doc.getSimulations().get(0);

        RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));
        onEdtRun(() -> invoke(panel, "runValidationCompare"));

        JLabel summary = field(panel, "validationSummary", JLabel.class);
        assertTrue(summary.getText().contains("no surface built"));
    }

    @Test
    public void testValidationParsesRowsAndComputesError() throws Exception {
        OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
        Simulation simulation = doc.getSimulations().get(0);
        simulation.getOptions().setRomDragSurface(constantSurface(0.40, 0.30));

        RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

        JTextArea input = field(panel, "validationInput", JTextArea.class);
        DefaultTableModel model = field(panel, "validationModel", DefaultTableModel.class);
        JLabel summary = field(panel, "validationSummary", JLabel.class);

        onEdtRun(() -> {
            input.setText("1.0,1.0e6,0.4\n");
            invoke(panel, "runValidationCompare");
        });

        assertEquals(1, model.getRowCount());
        assertTrue(summary.getText().contains("0.00%"));
    }

    @Test
    public void testGeometryWarningVisibleWhenSurfaceHashMismatches() throws Exception {
        OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
        Simulation simulation = doc.getSimulations().get(0);
        simulation.getOptions().setRomDragSurface(constantSurface(0.40, 0.30));

        RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

        JLabel warning = field(panel, "geometryWarning", JLabel.class);
        assertTrue(warning.isVisible());

        String matchingHash = info.openrocket.core.aerodynamics.rom.RomGeometryParameters.fromRocket(
                simulation.getRocket().getFlightConfiguration(simulation.getFlightConfigurationId())).geometryHash();
        simulation.getOptions().setRomDragSurface(surfaceWithHash(0.40, 0.30, matchingHash));

        onEdtRun(() -> invoke(panel, "refreshFromModel"));
        assertFalse(warning.isVisible());
    }

    @Test
    public void testValidationParsesBetaColumnFor4DSurface() throws Exception {
        OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
        Simulation simulation = doc.getSimulations().get(0);
        simulation.getOptions().setRomAeroSurface4D(constantSurface4D(HASH));

        RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

        JTextArea input = field(panel, "validationInput", JTextArea.class);
        DefaultTableModel model = field(panel, "validationModel", DefaultTableModel.class);

        onEdtRun(() -> {
            input.setText("1.0,1.0e6,15,0.4500\n");
            invoke(panel, "runValidationCompare");
        });

        assertEquals(1, model.getRowCount());
        assertEquals("15.0000", String.valueOf(model.getValueAt(0, 2)));
        assertEquals("0.4500", String.valueOf(model.getValueAt(0, 4)));
    }

    private static DragSurface constantSurface(double cdOff, double cdOn) {
        return surfaceWithHash(cdOff, cdOn, HASH);
    }

    private static DragSurface surfaceWithHash(double cdOff, double cdOn, String hash) {
        double[] mach = new double[] { 0.0, 2.0 };
        double[] logRe = new double[] { 4.0, 8.0 };
        double[] alpha = new double[] { 0.0, 10.0 };

        double[][][] off = new double[mach.length][logRe.length][alpha.length];
        double[][][] on = new double[mach.length][logRe.length][alpha.length];
        for (int i = 0; i < mach.length; i++) {
            for (int j = 0; j < logRe.length; j++) {
                for (int k = 0; k < alpha.length; k++) {
                    off[i][j][k] = cdOff;
                    on[i][j][k] = cdOn;
                }
            }
        }

        return new DragSurface(mach, logRe, alpha, off, on, hash, 0.0);
    }

    private static AeroSurface4D constantSurface4D(String hash) {
        double[] mach = new double[] { 0.0, 2.0 };
        double[] logRe = new double[] { 4.0, 8.0 };
        double[] alpha = new double[] { 0.0, 10.0 };
        double[] beta = new double[] { 0.0, 15.0 };

        double[][][][] off = new double[mach.length][logRe.length][alpha.length][beta.length];
        double[][][][] on = new double[mach.length][logRe.length][alpha.length][beta.length];
        double[][][][] body = new double[mach.length][logRe.length][alpha.length][beta.length];
        double[][][][] cn = new double[mach.length][logRe.length][alpha.length][beta.length];
        double[][][][] cm = new double[mach.length][logRe.length][alpha.length][beta.length];

        for (int i = 0; i < mach.length; i++) {
            for (int j = 0; j < logRe.length; j++) {
                for (int k = 0; k < alpha.length; k++) {
                    off[i][j][k][0] = 0.4000;
                    off[i][j][k][1] = 0.4500;
                    on[i][j][k][0] = 0.3000;
                    on[i][j][k][1] = 0.3500;
                    body[i][j][k][0] = 0.3600;
                    body[i][j][k][1] = 0.4100;
                }
            }
        }

        return new AeroSurface4D(mach, logRe, alpha, beta, off, on, body, cn, cm, hash, 4);
    }

    private static void invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T field(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        assertNotNull(value);
        return type.cast(value);
    }

    private static <T> T onEdt(EdtSupplier<T> supplier) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                ref.set(supplier.get());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return ref.get();
    }

    private static void onEdtRun(Runnable runnable) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    @FunctionalInterface
    private interface EdtSupplier<T> {
        T get() throws Exception;
    }
}

package info.openrocket.core.montecarlo;

import info.openrocket.core.startup.OpenRocketCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        /*
         * Standalone entry point used during development.
         *
         * This also "wires in" LandingDispersion6DOF so it is compiled into the
         * same jar when you build this module.
         *
         * NOTE:
         *   Your primary dispersion export path is now the GUI button which calls:
         *
         *     Path outDir = Paths.get(outputFolder);
         *     LandingDispersion6DOF.exportAll(outDir, "landing_dispersion", runRecords, launchLatDeg, launchLonDeg);
         *
         * Optional CLI mode:
         *   java -cp <your.jar> info.openrocket.core.montecarlo.Main dispersion <args...>
         *
         * If LandingDispersion6DOF still has a main(String[]), we will invoke it.
         * If not, we'll just print a message (so this file won't break your build).
         */
        if (args != null && args.length > 0 && "dispersion".equalsIgnoreCase(args[0])) {
            runDispersionCliIfPresent(args);
            return;
        }

        initializeOpenRocket();

        // SimulationOptionsFrame frame = new SimulationOptionsFrame();
        // frame.setVisible(true);
    }

    /**
     * Attempt to delegate to LandingDispersion6DOF.main(String[]) if it exists.
     * Uses reflection so Main.java doesn't fail compilation if you refactor/remove that CLI.
     */
    private static void runDispersionCliIfPresent(String[] args) throws Exception {
        // Ensure class is referenced so it gets compiled into the jar
        @SuppressWarnings("unused")
        Class<?> wiredIn = LandingDispersion6DOF.class;

        // Pass through args[1..]
        String[] pass = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, pass, 0, args.length - 1);
        }

        try {
            Method m = LandingDispersion6DOF.class.getMethod("main", String[].class);
            m.invoke(null, (Object) pass);
        } catch (NoSuchMethodException noCli) {
            log.warn("LandingDispersion6DOF.main(String[]) not found. Use the GUI export (exportAll) instead.");
            System.err.println(
                    "LandingDispersion6DOF CLI is not available in this build.\n" +
                    "Use the Simulation Extension GUI export (LandingDispersion6DOF.exportAll) instead."
            );
        }
    }

    /**
     * Inject required dependencies for OpenRocket, allowing us to run simulations
     * programmatically.
     * This initializes OpenRocket core services without requiring swing dependencies.
     */
    private static void initializeOpenRocket() {
        OpenRocketCore.initialize();
    }
}

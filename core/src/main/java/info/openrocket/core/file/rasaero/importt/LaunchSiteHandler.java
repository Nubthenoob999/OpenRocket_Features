package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.models.atmosphere.ExtendedISAModel;
import info.openrocket.core.simulation.SimulationOptions;
import org.xml.sax.SAXException;

import java.util.HashMap;

/**
 * Reads the RASAero launch site information, creates a new (empty) simulation
 * in the OpenRocket document and
 * applies the RASAero launch site information to the simulation.
 *
 * @author Sibo Van Gool <sibo.vangool@hotmail.com>
 */
public class LaunchSiteHandler extends AbstractElementHandler {
    private final SimulationOptions launchSiteSettings;
    private Double seaLevelBarometricPressurePa;

    public LaunchSiteHandler(final SimulationOptions launchSiteSettings) {
        this.launchSiteSettings = launchSiteSettings;
    }

    @Override
    public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
            throws SAXException {
        if (RASAeroCommonConstants.LAUNCH_ALTITUDE.equals(element)
                || RASAeroCommonConstants.LAUNCH_PRESSURE.equals(element)
                || RASAeroCommonConstants.LAUNCH_ROD_ANGLE.equals(element)
                || RASAeroCommonConstants.LAUNCH_ROD_LENGTH.equals(element)
                || RASAeroCommonConstants.LAUNCH_TEMPERATURE.equals(element)
                || RASAeroCommonConstants.LAUNCH_WIND_SPEED.equals(element)) {
            return PlainTextHandler.INSTANCE;
        }
        warnings.add("Unknown element " + element + " for launch site, ignoring.");
        return null;
    }

    @Override
    public void closeElement(String element, HashMap<String, String> attributes, String content, WarningSet warnings)
            throws SAXException {
        try {
            if (RASAeroCommonConstants.LAUNCH_ALTITUDE.equals(element)) {
                launchSiteSettings.setLaunchAltitude(
                        Double.parseDouble(content) / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_ALTITUDE);
                applyRasaeroAtmosphere();
            } else if (RASAeroCommonConstants.LAUNCH_PRESSURE.equals(element)) {
                double pressureInHg = Double.parseDouble(content);
                // A zero CDX1 value represents the blank optional field.  A
                // nonzero RASAero value is sea-level-corrected barometric
                // pressure, not the local station pressure OpenRocket stores.
                seaLevelBarometricPressurePa = pressureInHg > 0
                        ? pressureInHg / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_PRESSURE
                        : null;
                applyRasaeroAtmosphere();
            } else if (RASAeroCommonConstants.LAUNCH_ROD_ANGLE.equals(element)) {
                launchSiteSettings.setLaunchRodAngle(
                        Double.parseDouble(content) / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_ANGLE);
            } else if (RASAeroCommonConstants.LAUNCH_ROD_LENGTH.equals(element)) {
                launchSiteSettings.setLaunchRodLength(
                        Double.parseDouble(content) / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_ALTITUDE);
            } else if (RASAeroCommonConstants.LAUNCH_TEMPERATURE.equals(element)) {
                launchSiteSettings.setLaunchTemperature(
                        RASAeroCommonConstants.RASAERO_TO_OPENROCKET_TEMPERATURE(Double.parseDouble(content)));
                applyRasaeroAtmosphere();
            } else if (RASAeroCommonConstants.LAUNCH_WIND_SPEED.equals(element)) {
                launchSiteSettings.getAverageWindModel().setAverage(
                        Double.parseDouble(content) / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_SPEED);
            }
        } catch (NumberFormatException e) {
            warnings.add("Invalid number format for element " + element + ", ignoring.");
        }
    }

    /**
     * RASAero II User's Manual pp. 78-80: elevation supplies standard-day
     * pressure when barometric pressure is blank, while the entered launch
     * temperature independently anchors temperature and density.  When
     * present, the pressure input is sea-level-corrected barometric pressure.
     */
    private void applyRasaeroAtmosphere() {
        double altitude = launchSiteSettings.getLaunchAltitude();
        double standardSitePressure = new ExtendedISAModel()
                .getConditions(altitude).getPressure();
        double sitePressure = seaLevelBarometricPressurePa == null
                ? standardSitePressure
                : seaLevelBarometricPressurePa * standardSitePressure
                        / ExtendedISAModel.STANDARD_PRESSURE;
        launchSiteSettings.setISAAtmosphere(false);
        launchSiteSettings.setLaunchPressure(sitePressure);
    }
}

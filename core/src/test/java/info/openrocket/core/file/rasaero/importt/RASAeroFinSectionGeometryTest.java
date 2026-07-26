package info.openrocket.core.file.rasaero.importt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.rasaero.export.FinDTO;
import info.openrocket.core.logging.ErrorSet;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.util.BaseTestCase;

class RASAeroFinSectionGeometryTest extends BaseTestCase {
	private static final double INCHES_PER_METRE =
			RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH;

	@Test
	void hexagonalSectionDimensionsSurviveImportAndExport() throws Exception {
		BodyTube parent = new BodyTube();
		parent.setLength(1);
		parent.setOuterRadius(0.05);
		WarningSet warnings = new WarningSet();
		FinHandler handler = new FinHandler(parent, warnings);
		HashMap<String, String> attributes = new HashMap<>();

		close(handler, RASAeroCommonConstants.FIN_COUNT, "3", attributes,
				warnings);
		close(handler, RASAeroCommonConstants.FIN_CHORD, "12.5", attributes,
				warnings);
		close(handler, RASAeroCommonConstants.FIN_SPAN, "4.4", attributes,
				warnings);
		close(handler, RASAeroCommonConstants.FIN_SWEEP_DISTANCE, "8.5",
				attributes, warnings);
		close(handler, RASAeroCommonConstants.FIN_TIP_CHORD, "4", attributes,
				warnings);
		close(handler, RASAeroCommonConstants.FIN_THICKNESS, "0.125",
				attributes, warnings);
		close(handler, RASAeroCommonConstants.FIN_LE_RADIUS, "0.015",
				attributes, warnings);
		close(handler, RASAeroCommonConstants.AIRFOIL_SECTION, "Hexagonal",
				attributes, warnings);
		close(handler, RASAeroCommonConstants.FIN_FX1, "0.25", attributes,
				warnings);
		close(handler, RASAeroCommonConstants.FIN_FX3, "0.125", attributes,
				warnings);
		close(handler, RASAeroCommonConstants.LOCATION, "13.5", attributes,
				warnings);
		handler.endHandler(RASAeroCommonConstants.FIN, attributes, "",
				warnings);

		TrapezoidFinSet fin = (TrapezoidFinSet) parent.getChild(0);
		assertEquals(FinSet.CrossSection.AIRFOIL, fin.getCrossSection());
		assertEquals("Hexagonal", fin.getDetailedAirfoilSection());
		assertEquals(0.25 / INCHES_PER_METRE,
				fin.getLeadingEdgeAirfoilLength(), 1e-14);
		assertEquals(0.125 / INCHES_PER_METRE,
				fin.getTrailingEdgeAirfoilLength(), 1e-14);
		assertEquals(0.015 / INCHES_PER_METRE,
				fin.getLeadingEdgeRadius(), 1e-14);
		assertTrue(warnings.isEmpty());

		FinDTO exported = new FinDTO(fin, warnings, new ErrorSet());
		assertEquals("Hexagonal", exported.getAirfoilSection());
		assertEquals(0.25, exported.getFX1(), 1e-12);
		assertEquals(0.125, exported.getFX3(), 1e-12);
		assertEquals(0.015, exported.getLERadius(), 1e-12);
	}

	private static void close(FinHandler handler, String element,
			String content, HashMap<String, String> attributes,
			WarningSet warnings) throws Exception {
		handler.closeElement(element, attributes, content, warnings);
	}
}

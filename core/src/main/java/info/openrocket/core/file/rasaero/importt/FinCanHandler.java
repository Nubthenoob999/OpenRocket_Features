package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import org.xml.sax.SAXException;

import java.util.HashMap;

/**
 * Imports a RASAero fin can as one continuous outer mold line.
 *
 * <p>The CDX1 representation is an inline sleeve over the aft end of the
 * preceding body tube. Keeping that sleeve in an overlapping pod duplicates
 * external area and hides its shoulder from the supersonic geometry marcher.
 * Instead, shorten the preceding tube and insert the shoulder and fin-can tube
 * as ordinary siblings at the same physical stations.
 */
public class FinCanHandler extends BodyTubeHandler {
	private final RocketComponent parent;
	private final BodyTube parentBodyTube;

	private double insideDiameter;
	private double shoulderLength;

	public FinCanHandler(DocumentLoadingContext context, RocketComponent parent) {
		super(context);
		if (parent == null) {
			throw new IllegalArgumentException("The parent component of a fin can may not be null.");
		}
		if (parent.getChildCount() == 0) {
			throw new IllegalArgumentException("There is no component to attach the fin can to.");
		}
		RocketComponent lastChild = parent.getChild(parent.getChildCount() - 1);
		if (!(lastChild instanceof BodyTube bodyTube)) {
			throw new IllegalArgumentException("The parent component of a fin can must be a body tube.");
		}

		this.parent = parent;
		this.parentBodyTube = bodyTube;
		this.bodyTube.setName("Fin Can");
	}

	@Override
	public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
			throws SAXException {
		if (RASAeroCommonConstants.INSIDE_DIAMETER.equals(element)
				|| RASAeroCommonConstants.SHOULDER_LENGTH.equals(element)) {
			return PlainTextHandler.INSTANCE;
		}
		return super.openElement(element, attributes, warnings);
	}

	@Override
	public void closeElement(String element, HashMap<String, String> attributes, String content, WarningSet warnings)
			throws SAXException {
		super.closeElement(element, attributes, content, warnings);
		try {
			if (RASAeroCommonConstants.INSIDE_DIAMETER.equals(element)) {
				insideDiameter = Double.parseDouble(content)
						/ RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH;
			} else if (RASAeroCommonConstants.SHOULDER_LENGTH.equals(element)) {
				shoulderLength = Double.parseDouble(content)
						/ RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH;
			}
		} catch (NumberFormatException exception) {
			warnings.add("Could not convert " + element + " value of " + content
					+ ". It is expected to be a number.");
		}
	}

	@Override
	public void endHandler(String element, HashMap<String, String> attributes, String content, WarningSet warnings)
			throws SAXException {
		super.endHandler(element, attributes, content, warnings);
		this.bodyTube.setOuterRadiusAutomatic(false);

		// BodyTubeHandler briefly enables automatic radius while this component
		// is still unattached. Restore the explicit CDX1 diameter afterward.
		if (diameter != null && diameter > 0) {
			this.bodyTube.setOuterRadius(diameter / 2.0
					/ RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH);
		}

		Transition shoulder = new Transition();
		shoulder.setName("Fin Can Shoulder");
		shoulder.setShapeType(Transition.Shape.CONICAL);
		shoulder.setForeRadiusAutomatic(false);
		shoulder.setAftRadiusAutomatic(false);
		shoulder.setLength(shoulderLength);
		shoulder.setForeRadius(insideDiameter / 2);
		shoulder.setAftRadius(bodyTube.getOuterRadius());
		shoulder.setThickness(bodyTube.getThickness());
		shoulder.setColor(bodyTube.getColor());

		double sleeveLength = shoulderLength + this.bodyTube.getLength();
		double newParentLength = parentBodyTube.getLength() - sleeveLength;
		if (newParentLength < 0) {
			warnings.add("Fin can (length " + sleeveLength
					+ " m) is longer than the parent body tube (length "
					+ parentBodyTube.getLength() + " m); clamping to 0.");
			newParentLength = 0;
		}
		parentBodyTube.setLength(newParentLength);

		parent.addChild(shoulder);
		parent.addChild(this.bodyTube);
	}
}

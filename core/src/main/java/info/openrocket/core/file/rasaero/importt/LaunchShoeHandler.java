package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.position.AxialMethod;

/**
 * Preserves RASAero's two launch shoes as solid square projected-area
 * protuberances. OpenRocket has no dedicated launch-shoe component, so a
 * zero-notch {@link RailButton} is used as the geometry carrier.
 */
public final class LaunchShoeHandler {
	private LaunchShoeHandler() {
	}

	public static void addLaunchShoes(BodyTube parent, double areaSquareInches) {
		if (parent == null || !(areaSquareInches > 0)
				|| !Double.isFinite(areaSquareInches)) {
			throw new IllegalArgumentException("invalid launch-shoe input");
		}
		double lengthScale = RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH;
		double areaM2 = areaSquareInches / (lengthScale * lengthScale);
		double side = Math.sqrt(areaM2);
		RailButton shoe = new RailButton();
		shoe.setOuterDiameter(side);
		shoe.setInnerDiameter(side);
		shoe.setTotalHeight(side);
		shoe.setBaseHeight(side / 2);
		shoe.setFlangeHeight(side / 2);
		shoe.setScrewHeight(0);
		shoe.setInstanceCount(2);
		shoe.setInstanceSeparation(Math.max(0,
				parent.getLength() - 0.0508 - side));
		shoe.setName("Launch Shoe");
		shoe.setAxialMethod(AxialMethod.TOP);
		shoe.setAxialOffset(0.0254 + side / 2);
		ColorHandler.applyRASAeroColor(shoe, null);
		parent.addChild(shoe);
	}
}

package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import org.xml.sax.SAXException;

import java.util.HashMap;

/**
 * Imports a RASAero boattail into OpenRocket's continuous symmetric-component
 * chain.  Keeping the transition as a direct stage child ensures the
 * supersonic geometry and base-drag calculations can traverse it.
 *
 * @author Sibo Van Gool <sibo.vangool@hotmail.com>
 */
public class BoattailHandler extends TransitionHandler {

    /**
     * Constructor
     *
     * @param context  current document loading context
     * @param parent   parent stage to add this new component to
     * @param warnings warning set to add import warnings to
     * @throws IllegalArgumentException if the parent component is null
     */
    public BoattailHandler(DocumentLoadingContext context, RocketComponent parent, WarningSet warnings)
            throws IllegalArgumentException {
        super(context);

        if (parent == null) {
            throw new IllegalArgumentException("The parent component of a boattail may not be null.");
        }
        if (parent.getChildCount() == 0) {
            throw new IllegalArgumentException("The parent component of a boattail must have at least one child.");
        }

        // RASAero's boattail offset is not currently consumed by the importer.
        // For the supported zero-offset geometry, keep the transition in the
        // normal linear body chain instead of hiding it inside an inline pod.
        RocketComponent lastChild = parent.getChild(parent.getChildCount() - 1);
        if (lastChild instanceof BodyTube || lastChild instanceof Transition) {
            parent.addChild(this.transition);
        } else {
            throw new IllegalArgumentException(
                    "Cannot add boattail after component of type " + lastChild.getClass().getName());
        }

        this.transition.setAftRadiusAutomatic(false);
        this.transition.setShapeType(Transition.Shape.CONICAL); // RASAero only supports conical boattails
        this.transition.setName("Boattail");
    }

    @Override
    public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
            throws SAXException {
        if (RASAeroCommonConstants.FIN.equals(element)) {
            return new FinHandler(this.transition, warnings);
        }
        return super.openElement(element, attributes, warnings);
    }
}

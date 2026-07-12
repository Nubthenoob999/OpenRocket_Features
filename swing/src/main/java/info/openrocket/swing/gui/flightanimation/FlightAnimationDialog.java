package info.openrocket.swing.gui.dialogs.flightanimation;

import java.awt.Window;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.swing.gui.simulation.SimulationReplayDialog;

/**
 * Compatibility entry point for the Tools > Flight animation action.
 */
public class FlightAnimationDialog extends SimulationReplayDialog {

	public FlightAnimationDialog(Window parent, Simulation simulation) {
		this(parent, null, simulation);
	}

	public FlightAnimationDialog(Window parent, OpenRocketDocument document, Simulation simulation) {
		super(parent, document, simulation);
	}
}

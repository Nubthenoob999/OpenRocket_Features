package info.openrocket.swing.gui.simulation;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;

import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.startup.Application;

/**
 * A dialog window that hosts a {@link SimulationReplayPanel} for 3D flight replay.
 */
public class SimulationReplayDialog extends JDialog {

    private static final Translator trans = Application.getTranslator();

    private final OpenRocketDocument document;
    private SimulationReplayPanel replayPanel;
    private Simulation displayedSimulation;

    public SimulationReplayDialog(Window parent, OpenRocketDocument document, Simulation simulation) {
        super(parent, "3D Flight Animation - " + simulation.getName(), ModalityType.MODELESS);
        this.document = document;
        this.displayedSimulation = simulation;

        JPanel content = new JPanel(new BorderLayout());
        if (document != null && getReplayableSimulationCount() > 1) {
            content.add(createSimulationSelector(), BorderLayout.NORTH);
        }
        replaceReplayPanel(simulation);
        content.add(replayPanel, BorderLayout.CENTER);
        setContentPane(content);

        // GLJPanel reports zero preferred height before first render; pack() would
        // collapse the dialog. setSize() bypasses preferred-size negotiation entirely.
        // Wider than the bare 3D view to give the Mission Control telemetry panel room.
        setSize(1280, 760);
        setMinimumSize(new Dimension(800, 460));
        setLocationRelativeTo(parent);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                replayPanel.cleanup();
            }
        });
    }

    private JPanel createSimulationSelector() {
        JPanel selectorPanel = new JPanel(new BorderLayout(8, 0));
        selectorPanel.add(new JLabel(trans.get("simulation.replay.selector")), BorderLayout.WEST);

        JComboBox<Simulation> selector = new JComboBox<>();
        for (Simulation candidate : document.getSimulations()) {
            if (hasFlightData(candidate)) {
                selector.addItem(candidate);
            }
        }
        selector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Simulation simulation) {
                    setText(simulation.getName());
                }
                return this;
            }
        });
        selector.setSelectedItem(displayedSimulation);
        selector.addActionListener(event -> {
            Simulation selected = (Simulation) selector.getSelectedItem();
            if (selected != null && selected != displayedSimulation) {
                switchSimulation(selected);
            }
        });
        selectorPanel.add(selector, BorderLayout.CENTER);
        return selectorPanel;
    }

    private static boolean hasFlightData(Simulation simulation) {
        return simulation.getSimulatedData() != null
                && simulation.getSimulatedData().getBranchCount() > 0;
    }

    private int getReplayableSimulationCount() {
        int count = 0;
        for (Simulation candidate : document.getSimulations()) {
            if (hasFlightData(candidate)) {
                count++;
            }
        }
        return count;
    }

    private void switchSimulation(Simulation simulation) {
        replayPanel.cleanup();
        displayedSimulation = simulation;
        setTitle("3D Flight Animation - " + simulation.getName());

        JPanel content = (JPanel) getContentPane();
        content.remove(replayPanel);
        replaceReplayPanel(simulation);
        content.add(replayPanel, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private void replaceReplayPanel(Simulation simulation) {
        replayPanel = new SimulationReplayPanel(document, simulation);
    }
}

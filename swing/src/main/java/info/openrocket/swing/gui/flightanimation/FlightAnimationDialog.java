package info.openrocket.swing.gui.dialogs.flightanimation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;

/**
 * A dialog that replays a completed rocket simulation as a 2D animated flight path visualization.
 * <p>
 * Shows the rocket trajectory with altitude, velocity, and key flight events such as
 * launch, burnout, apogee, recovery deployment, and landing. Provides playback controls
 * including play/pause, speed adjustment, and a timeline scrubber.
 *
 * @author Alex Zoghlin
 */
public class FlightAnimationDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private static final int TIMER_DELAY_MS = 30; // ~33 fps
	private static final Font READOUT_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);
	private static final Font READOUT_LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

	private final FlightAnimationPanel animationPanel;
	private final JSlider timelineSlider;
	private final JButton playPauseButton;
	private final JLabel timeLabel;
	private final JLabel altitudeLabel;
	private final JLabel velocityLabel;
	private final JLabel machLabel;
	private final JLabel accelerationLabel;
	private final JLabel eventLabel;

	private final Timer animationTimer;
	private boolean playing = false;
	private double playbackSpeed = 1.0;

	private List<Double> timeData;
	private List<Double> altitudeData;
	private List<Double> velocityData;
	private List<Double> machData;
	private List<Double> accelData;
	private List<FlightEvent> events;

	/**
	 * Creates a new FlightAnimationDialog for the given simulation.
	 *
	 * @param parent     the parent window
	 * @param simulation the simulation to animate (must already be simulated)
	 */
	public FlightAnimationDialog(Window parent, Simulation simulation) {
		super(parent, "Flight Animation - " + simulation.getName(), ModalityType.MODELESS);

		FlightData flightData = simulation.getSimulatedData();
		if (flightData == null || flightData.getBranchCount() == 0) {
			JOptionPane.showMessageDialog(parent,
					"No simulation data available. Please run the simulation first.",
					"No Data", JOptionPane.WARNING_MESSAGE);
			dispose();

			// Initialize final fields to keep compiler happy
			animationPanel = null;
			timelineSlider = null;
			playPauseButton = null;
			timeLabel = null;
			altitudeLabel = null;
			velocityLabel = null;
			machLabel = null;
			accelerationLabel = null;
			eventLabel = null;
			animationTimer = null;
			return;
		}

		FlightDataBranch branch = flightData.getBranch(0);

		// Load data arrays
		timeData = branch.get(FlightDataType.TYPE_TIME);
		altitudeData = branch.get(FlightDataType.TYPE_ALTITUDE);
		velocityData = branch.get(FlightDataType.TYPE_VELOCITY_TOTAL);
		machData = branch.get(FlightDataType.TYPE_MACH_NUMBER);
		accelData = branch.get(FlightDataType.TYPE_ACCELERATION_TOTAL);
		events = branch.getEvents();

		// --- Build UI ---
		JPanel mainPanel = new JPanel(new MigLayout("fill, ins 8", "[grow]", "[grow][][][]"));

		// Animation panel
		animationPanel = new FlightAnimationPanel();
		animationPanel.loadData(branch);
		animationPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
		mainPanel.add(animationPanel, "grow, push, wrap");

		// Data readouts panel
		JPanel readoutPanel = new JPanel(new MigLayout("ins 4, gap 16", "[][][][][][]", "[][]"));
		readoutPanel.setBorder(BorderFactory.createTitledBorder("Flight Data"));

		readoutPanel.add(createReadoutLabel("Time:"), "");
		timeLabel = createReadoutValue("0.00 s");
		readoutPanel.add(timeLabel, "width 90!");

		readoutPanel.add(createReadoutLabel("Altitude:"), "");
		altitudeLabel = createReadoutValue("0.0 m");
		readoutPanel.add(altitudeLabel, "width 100!");

		readoutPanel.add(createReadoutLabel("Velocity:"), "");
		velocityLabel = createReadoutValue("0.0 m/s");
		readoutPanel.add(velocityLabel, "width 100!, wrap");

		readoutPanel.add(createReadoutLabel("Mach:"), "");
		machLabel = createReadoutValue("0.000");
		readoutPanel.add(machLabel, "width 90!");

		readoutPanel.add(createReadoutLabel("Accel:"), "");
		accelerationLabel = createReadoutValue("0.0 m/s\u00B2");
		readoutPanel.add(accelerationLabel, "width 100!");

		readoutPanel.add(createReadoutLabel("Event:"), "");
		eventLabel = createReadoutValue("-");
		readoutPanel.add(eventLabel, "width 100!, wrap");

		mainPanel.add(readoutPanel, "growx, wrap");

		// Timeline slider
		int dataSize = timeData != null ? timeData.size() : 0;
		timelineSlider = new JSlider(0, Math.max(1, dataSize - 1), 0);
		timelineSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int idx = timelineSlider.getValue();
				updateDisplay(idx);
			}
		});
		mainPanel.add(timelineSlider, "growx, wrap");

		// Controls panel
		JPanel controlsPanel = new JPanel(new MigLayout("ins 4", "[][][][grow][]", "[]"));

		playPauseButton = new JButton("\u25B6 Play");
		playPauseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				togglePlayback();
			}
		});
		controlsPanel.add(playPauseButton, "width 100!");

		JButton resetButton = new JButton("\u23EE Reset");
		resetButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stopPlayback();
				timelineSlider.setValue(0);
			}
		});
		controlsPanel.add(resetButton, "");

		controlsPanel.add(new JLabel("Speed:"), "gapleft 16");
		JComboBox<String> speedCombo = new JComboBox<>(new String[]{"0.25x", "0.5x", "1x", "2x", "4x", "8x"});
		speedCombo.setSelectedIndex(2); // default 1x
		speedCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String sel = (String) speedCombo.getSelectedItem();
				if (sel != null) {
					playbackSpeed = Double.parseDouble(sel.replace("x", ""));
				}
			}
		});
		controlsPanel.add(speedCombo, "");

		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		controlsPanel.add(closeButton, "gapleft push");

		mainPanel.add(controlsPanel, "growx");

		add(mainPanel);

		// Animation timer
		animationTimer = new Timer(TIMER_DELAY_MS, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				advanceFrame();
			}
		});

		// Clean up on close
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				stopPlayback();
			}
		});

		setSize(900, 700);
		setLocationRelativeTo(parent);
		updateDisplay(0);
	}

	private JLabel createReadoutLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(READOUT_LABEL_FONT);
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		return label;
	}

	private JLabel createReadoutValue(String text) {
		JLabel label = new JLabel(text);
		label.setFont(READOUT_FONT);
		label.setForeground(new Color(0, 80, 160));
		return label;
	}

	private void togglePlayback() {
		if (playing) {
			stopPlayback();
		} else {
			startPlayback();
		}
	}

	private void startPlayback() {
		if (timeData == null || timeData.isEmpty()) return;
		// If at end, restart
		if (timelineSlider.getValue() >= timelineSlider.getMaximum()) {
			timelineSlider.setValue(0);
		}
		playing = true;
		playPauseButton.setText("\u23F8 Pause");
		animationTimer.start();
	}

	private void stopPlayback() {
		playing = false;
		playPauseButton.setText("\u25B6 Play");
		animationTimer.stop();
	}

	private void advanceFrame() {
		if (timeData == null || timeData.isEmpty()) return;

		int current = timelineSlider.getValue();
		int max = timelineSlider.getMaximum();

		// Calculate how many data points to advance based on playback speed
		// At 1x speed, we want real-time playback
		double timerSeconds = TIMER_DELAY_MS / 1000.0;
		double simTimeAdvance = timerSeconds * playbackSpeed;

		double currentTime = timeData.get(current);
		double targetTime = currentTime + simTimeAdvance;

		// Find the index closest to the target time
		int newIndex = current;
		while (newIndex < max && timeData.get(newIndex) < targetTime) {
			newIndex++;
		}
		// Always advance at least one frame
		if (newIndex == current && current < max) {
			newIndex = current + 1;
		}

		if (newIndex >= max) {
			timelineSlider.setValue(max);
			stopPlayback();
		} else {
			timelineSlider.setValue(newIndex);
		}
	}

	private void updateDisplay(int index) {
		if (timeData == null || index < 0 || index >= timeData.size()) return;

		animationPanel.setCurrentIndex(index);

		// Update readouts
		Double time = timeData.get(index);
		timeLabel.setText(time != null ? String.format("%.2f s", time) : "-");

		if (altitudeData != null && index < altitudeData.size()) {
			Double alt = altitudeData.get(index);
			altitudeLabel.setText(alt != null ? String.format("%.1f m", alt) : "-");
		}

		if (velocityData != null && index < velocityData.size()) {
			Double vel = velocityData.get(index);
			velocityLabel.setText(vel != null ? String.format("%.1f m/s", vel) : "-");
		}

		if (machData != null && index < machData.size()) {
			Double mach = machData.get(index);
			machLabel.setText(mach != null ? String.format("%.3f", mach) : "-");
		}

		if (accelData != null && index < accelData.size()) {
			Double acc = accelData.get(index);
			accelerationLabel.setText(acc != null ? String.format("%.1f m/s\u00B2", acc) : "-");
		}

		// Find the most recent event at or before current time
		updateEventLabel(time);
	}

	private void updateEventLabel(Double currentTime) {
		if (events == null || currentTime == null) {
			eventLabel.setText("-");
			return;
		}

		FlightEvent latestEvent = null;
		for (FlightEvent event : events) {
			FlightEvent.Type type = event.getType();
			if (type == FlightEvent.Type.LAUNCH ||
					type == FlightEvent.Type.LIFTOFF ||
					type == FlightEvent.Type.BURNOUT ||
					type == FlightEvent.Type.APOGEE ||
					type == FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT ||
					type == FlightEvent.Type.GROUND_HIT) {
				if (event.getTime() <= currentTime) {
					latestEvent = event;
				}
			}
		}

		if (latestEvent != null) {
			eventLabel.setText(latestEvent.getType().toString());
		} else {
			eventLabel.setText("-");
		}
	}
}

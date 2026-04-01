package info.openrocket.swing.gui.simulation;

import java.awt.Window;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EventObject;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import info.openrocket.core.l10n.Translator;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.simulation.LiveWeatherService;
import info.openrocket.core.simulation.LiveWeatherService.LiveWeatherException;
import info.openrocket.core.simulation.LiveWeatherService.LiveWeatherRequest;
import info.openrocket.core.simulation.LiveWeatherService.LiveWeatherResult;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.StateChangeListener;
import net.miginfocom.swing.MigLayout;

class LiveWeatherSettingsPanel extends JPanel {
	private static final Translator trans = Application.getTranslator();

	private final SimulationOptions options;
	private final Runnable onWindProfileApplied;
	private final LiveWeatherService liveWeatherService;

	private final JLabel latitudeValue = new JLabel();
	private final JLabel longitudeValue = new JLabel();
	private final JTextField dateField = new JTextField(10);
	private final JComboBox<String> timeCombo = new JComboBox<>();
	private final JLabel statusLabel = new JLabel();
	private final JLabel previewLabel = new JLabel(trans.get("simedtdlg.lbl.liveweather.preview.empty"));
	private final JButton fetchButton = new JButton(trans.get("simedtdlg.but.liveweather.fetch"));
	private final JButton applyButton = new JButton(trans.get("simedtdlg.but.liveweather.apply"));

	private LiveWeatherResult latestResult;

	LiveWeatherSettingsPanel(SimulationOptions options, Runnable onWindProfileApplied) {
		this(options, onWindProfileApplied, new LiveWeatherService());
	}

	LiveWeatherSettingsPanel(SimulationOptions options, Runnable onWindProfileApplied, LiveWeatherService liveWeatherService) {
		super(new MigLayout("fillx, ins 0", "[grow][grow]", ""));
		this.options = options;
		this.onWindProfileApplied = onWindProfileApplied;
		this.liveWeatherService = liveWeatherService;

		buildUi();
		refreshCoordinates();
		initializeInputs();

		options.addChangeListener(new StateChangeListener() {
			@Override
			public void stateChanged(EventObject e) {
				refreshCoordinates();
			}
		});
	}

	private void buildUi() {
		add(new JLabel(trans.get("simedtdlg.lbl.liveweather.latitude")), "split 2");
		add(latitudeValue, "wrap");

		add(new JLabel(trans.get("simedtdlg.lbl.liveweather.longitude")), "split 2");
		add(longitudeValue, "wrap");

		add(new JLabel(trans.get("simedtdlg.lbl.liveweather.date")));
		add(dateField, "growx, wrap");

		add(new JLabel(trans.get("simedtdlg.lbl.liveweather.time")));
		add(timeCombo, "growx, wrap");

		add(new JLabel(trans.get("simedtdlg.lbl.liveweather.defaults")));
		add(new JLabel(trans.get("simedtdlg.lbl.liveweather.defaults.value")), "wrap");

		add(statusLabel, "spanx, growx, wrap");
		add(previewLabel, "spanx, growx, wrap para");

		JPanel buttons = new JPanel(new MigLayout("ins 0"));
		JButton editButton = new JButton(trans.get("simedtdlg.but.editWindLevels"));
		buttons.add(fetchButton);
		buttons.add(applyButton);
		buttons.add(editButton);
		add(buttons, "spanx, growx, wrap");

		fetchButton.addActionListener(e -> fetchWeather());
		applyButton.addActionListener(e -> applyFetchedWeather());
		editButton.addActionListener(e -> openMultiLevelEditor());
		applyButton.setEnabled(false);

		dateField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				storeDate();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				storeDate();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				storeDate();
			}
		});

		timeCombo.addActionListener(e -> {
			Object selected = timeCombo.getSelectedItem();
			options.setLiveWeatherLaunchTime(selected != null ? selected.toString() : "");
		});
	}

	private void initializeInputs() {
		for (int hour = 0; hour < 24; hour++) {
			timeCombo.addItem(String.format("%02d:00", hour));
		}

		String savedDate = options.getLiveWeatherLaunchDate();
		if (savedDate == null || savedDate.isBlank()) {
			savedDate = LocalDate.now().toString();
			options.setLiveWeatherLaunchDate(savedDate);
		}
		dateField.setText(savedDate);

		String savedTime = options.getLiveWeatherLaunchTime();
		if (savedTime == null || savedTime.isBlank()) {
			savedTime = String.format("%02d:00", LocalTime.now().getHour());
			options.setLiveWeatherLaunchTime(savedTime);
		}
		timeCombo.setSelectedItem(savedTime);
	}

	private void refreshCoordinates() {
		latitudeValue.setText(UnitGroup.UNITS_LATITUDE.toStringUnit(options.getLaunchLatitude()));
		longitudeValue.setText(UnitGroup.UNITS_LONGITUDE.toStringUnit(options.getLaunchLongitude()));
	}

	private void storeDate() {
		options.setLiveWeatherLaunchDate(dateField.getText().trim());
	}

	private void fetchWeather() {
		LiveWeatherRequest request;
		try {
			request = new LiveWeatherRequest(options.getLaunchLatitude(), options.getLaunchLongitude(),
					LocalDate.parse(dateField.getText().trim()),
					LocalTime.parse(timeCombo.getSelectedItem().toString()));
		} catch (DateTimeParseException ex) {
			JOptionPane.showMessageDialog(this,
					trans.get("simedtdlg.msg.liveweather.invaliddatetime"),
					trans.get("simedtdlg.msg.liveweather.error.title"),
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		fetchButton.setEnabled(false);
		applyButton.setEnabled(false);
		statusLabel.setText(trans.get("simedtdlg.lbl.liveweather.status.fetching"));
		previewLabel.setText(trans.get("simedtdlg.lbl.liveweather.preview.empty"));
		latestResult = null;

		SwingWorker<LiveWeatherResult, Void> worker = new SwingWorker<>() {
			@Override
			protected LiveWeatherResult doInBackground() throws Exception {
				return liveWeatherService.fetchWeather(request);
			}

			@Override
			protected void done() {
				fetchButton.setEnabled(true);
				try {
					latestResult = get();
					statusLabel.setText(String.format(trans.get("simedtdlg.lbl.liveweather.status.success"),
							latestResult.source(), latestResult.fetchedAtLabel()));
					previewLabel.setText(buildPreviewHtml(latestResult));
					applyButton.setEnabled(true);
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					String message = cause instanceof LiveWeatherException ? cause.getMessage() : trans.get("simedtdlg.msg.liveweather.fetchfailed");
					statusLabel.setText(trans.get("simedtdlg.lbl.liveweather.status.failed"));
					JOptionPane.showMessageDialog(LiveWeatherSettingsPanel.this, message,
							trans.get("simedtdlg.msg.liveweather.error.title"), JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		worker.execute();
	}

	private String buildPreviewHtml(LiveWeatherResult result) {
		int count = result.levels().size();
		int minAltitude = result.levels().get(0).altitudeMeters();
		int maxAltitude = result.levels().get(result.levels().size() - 1).altitudeMeters();
		double minSpeed = result.levels().stream().mapToDouble(l -> l.speedMetersPerSecond()).min().orElse(0);
		double maxSpeed = result.levels().stream().mapToDouble(l -> l.speedMetersPerSecond()).max().orElse(0);

		StringBuilder builder = new StringBuilder("<html>");
		builder.append(String.format(trans.get("simedtdlg.lbl.liveweather.preview.summary"), count)).append("<br>");
		builder.append(String.format(trans.get("simedtdlg.lbl.liveweather.preview.altitude"), minAltitude, maxAltitude)).append("<br>");
		builder.append(String.format(trans.get("simedtdlg.lbl.liveweather.preview.speed"), minSpeed, maxSpeed));
		if (result.surfaceTemperatureC() != null) {
			builder.append("<br>").append(String.format(trans.get("simedtdlg.lbl.liveweather.preview.temperature"),
					result.surfaceTemperatureC()));
		}
		builder.append("</html>");
		return builder.toString();
	}

	private void applyFetchedWeather() {
		if (latestResult == null) {
			return;
		}
		MultiLevelPinkNoiseWindModel model = options.getMultiLevelWindModel();
		LiveWeatherService.applyLevelsToModel(model, latestResult.levels());
		options.setWindModelType(info.openrocket.core.models.wind.WindModelType.MULTI_LEVEL);
		options.setLiveWeatherDataSelected(true);
		applyButton.setEnabled(false);
		onWindProfileApplied.run();
		statusLabel.setText(trans.get("simedtdlg.lbl.liveweather.status.applied"));
	}

	private void openMultiLevelEditor() {
		Window owner = SwingUtilities.getWindowAncestor(this);
		MultiLevelWindEditDialog dialog = new MultiLevelWindEditDialog(owner, options.getMultiLevelWindModel());
		dialog.setVisible(true);
		onWindProfileApplied.run();
	}
}

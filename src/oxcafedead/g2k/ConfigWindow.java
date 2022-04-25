package oxcafedead.g2k;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.java.games.input.Controller;
import net.java.games.input.Event;

public class ConfigWindow {

	private final JFrame frame;
	private BindingsProfile bindingsProfile = null;

	AtomicInteger selectedWinCode = new AtomicInteger(-1);
	AtomicBoolean inWinCodeBindingProcess = new AtomicBoolean(false);
	AtomicInteger analogSensitivity = new AtomicInteger(60);


	KeyAdapter listener = new KeyAdapter() {
		@Override
		public void keyPressed(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.KEY_LOCATION_UNKNOWN) {
				Main.info("key is unknown " + e);
				return;
			}
			Main.debug("key pressed " + e);
			selectedWinCode.set(e.getKeyCode());
		}
	};

	public ConfigWindow(Controller controller) {
		frame = new JFrame("Gamepad 2 Keyboard Converter | Settings");

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
			// okay, let it be the standard one...
		}


		frame.setSize(500, 300);
		frame.setResizable(false);

		JPanel framePanel = new JPanel();
		framePanel.setLayout(new BoxLayout(framePanel, BoxLayout.PAGE_AXIS));
		draw(framePanel);
		frame.add(framePanel);

		frame.setIconImage(Main.icon());

		GraphicsConfiguration graphicsConfiguration =
				GraphicsEnvironment.getLocalGraphicsEnvironment()
						.getDefaultScreenDevice()
						.getDefaultConfiguration();
		frame.setVisible(true);


		frame.setLocation(
				(graphicsConfiguration.getBounds().width - 500) / 2,
				(graphicsConfiguration.getBounds().height - 300) / 2);

		frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	private void draw(JPanel framePanel) {

		JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		var persiter = new BindingProfilePersiter();

		var loadBtn = new JButton("Load");
		var nameFld = new JTextField(bindingsProfile == null ? "default" : bindingsProfile.getName());
		nameFld.setPreferredSize(new Dimension(100, 21));
		if (bindingsProfile == null) loadProfile(framePanel, persiter, nameFld, false);
		loadBtn.addActionListener(a -> {
			loadProfile(framePanel, persiter, nameFld, true);
		});
		filePanel.add(loadBtn);
		var saveBtn = new JButton("Save");
		saveBtn.addActionListener(a -> {
			try {
				bindingsProfile.setName(nameFld.getText());
				persiter.save(bindingsProfile, new File(".").toPath());
				JOptionPane.showMessageDialog(framePanel, "Saved successfully!");
			}
			catch (IOException | InvalidPathException e) {
				JOptionPane.showMessageDialog(framePanel, "Could not save!" + e.getMessage());
			}
		});
		filePanel.add(saveBtn);
		filePanel.add(nameFld);


		nameFld.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				var nameFldText = nameFld.getText();
				var blankName = nameFldText.isBlank();
				loadBtn.setEnabled(!blankName);
				saveBtn.setEnabled(!blankName);

				if (!blankName) {
					bindingsProfile.setName(nameFldText);
				}
			}
		});

		framePanel.add(filePanel);

		JPanel allBindingsPanel = new JPanel();
		allBindingsPanel.setLayout(new BoxLayout(allBindingsPanel, BoxLayout.PAGE_AXIS));
		allBindingsPanel.setAutoscrolls(true);
		JScrollPane scrollPane = new JScrollPane(allBindingsPanel);
		var chcks = new ArrayList<JCheckBox>();

		var remBtn = new JButton("Remove selected");

		bindingsProfile.getAllMappings().forEach((k, v) -> {
			JPanel bindingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			var gamepadCodeBtn = new JButton(gamePadKeyDisplayName(k));
			gamepadCodeBtn.setName(k);
			var winCodeBtn = new JButton(winKeyDisplayName(v));
			winCodeBtn.setName(v + "");
			gamepadCodeBtn.addActionListener(e -> {
				if (Util.isBindingMode()) {
					return;
				}
				Executors.newSingleThreadExecutor().submit(() -> {

					Main.debug("searching for a new gp code");
					var event = Util.nextBindingEvent();
					if (event == null) {
						Main.debug("gp code null");
						// skip
						return;
					}
					Main.debug("prev gp code = " + k);
					bindingsProfile.removeMapping(k);
					var component = event.getComponent();
					bindingsProfile.setMapping(event, Integer.parseInt(winCodeBtn.getName()));
					Main.debug("new gp code = " + eventToGamepadCode(event));

					refresh(framePanel);
				});
			});
			winCodeBtn.addKeyListener(listener);
			winCodeBtn.addActionListener(e -> {
				if (inWinCodeBindingProcess.get()) {
					return;
				}
				inWinCodeBindingProcess.set(true);
				Executors.newSingleThreadExecutor().submit(() -> {
					Main.debug("searching for a new code");
					while (this.selectedWinCode.get() == -1) {
						try {
							Thread.sleep(100);
						}
						catch (InterruptedException ex) {
							ex.printStackTrace();
							return;
						}
					}
					var winCode = this.selectedWinCode.get();
					Main.debug("new code: winCode" + winCode + ", " + KeyEvent.getKeyText(winCode));
					bindingsProfile.setMapping(gamepadCodeBtn.getName(), winCode);
					this.selectedWinCode.set(-1);
					inWinCodeBindingProcess.set(false);
					refresh(framePanel);
				});
			});
			var remChk = new JCheckBox();
			remChk.setName(gamepadCodeBtn.getName());
			remChk.addChangeListener(l -> remBtn.setEnabled(chcks.stream().anyMatch(AbstractButton::isSelected)));
			chcks.add(remChk);
			bindingPanel.add(remChk);
			bindingPanel.add(new JLabel("Binding:"));
			bindingPanel.add(gamepadCodeBtn);
			bindingPanel.add(new JLabel("->"));
			bindingPanel.add(winCodeBtn);
			allBindingsPanel.add(bindingPanel);
		});
		framePanel.add(scrollPane);


		JPanel actionsPanel = new JPanel();
		actionsPanel.add(Box.createHorizontalStrut(5));
		var actionLayout = new BoxLayout(actionsPanel, BoxLayout.X_AXIS);
		actionsPanel.setAlignmentY(.5f);
		actionsPanel.setLayout(actionLayout);
		actionsPanel.setMinimumSize(new Dimension(200, 50));
		var newBtn = new JButton("New Binding");
		newBtn.addActionListener(a -> {
			Executors.newSingleThreadExecutor().submit(() -> {
				bindingsProfile.setMapping("click and type a gamepad key", -1);
				refresh(framePanel);
			});
		});
		remBtn.setEnabled(false);
		remBtn.addActionListener(a -> {
			chcks.stream()
					.filter(AbstractButton::isSelected)
					.map(Component::getName)
					.forEach(bindingsProfile::removeMapping);
			refresh(framePanel);
		});
		actionsPanel.add(remBtn);
		actionsPanel.add(Box.createHorizontalStrut(5));
		actionsPanel.add(newBtn);
		actionsPanel.add(Box.createHorizontalGlue());
		actionsPanel.add(new JLabel("Analog sensitivity"));
		var analogSensitivitySlider = new JSlider();
		analogSensitivitySlider.setValue(bindingsProfile.analogSensitivity());
		actionsPanel.add(analogSensitivitySlider);
		analogSensitivitySlider.setPreferredSize(new Dimension(50, 8));
		analogSensitivitySlider.setMinimum(1);
		analogSensitivitySlider.addChangeListener(l -> {
			var sensitivity = analogSensitivitySlider.getValue();
			Main.debug("Changing sesitivity to " + sensitivity + ", float = " + sensitivity / 100f);
			bindingsProfile.setAnalogSensitivity(sensitivity);
		});
		actionsPanel.add(Box.createHorizontalStrut(5));
		framePanel.add(actionsPanel);
	}

	private void loadProfile(JPanel framePanel, BindingProfilePersiter persiter, JTextField nameFld, boolean refresh) {
		try {
			bindingsProfile = persiter.load(new File(Paths.get("").toAbsolutePath() + "/" + nameFld.getText() + ".g2k"));
			if (refresh) refresh(framePanel);
		}
		catch (IOException | InvalidPathException e) {
			JOptionPane.showMessageDialog(framePanel, "Could not load! " + e.getMessage());
		}
	}

	private void refresh(JPanel framePanel) {
		framePanel.removeAll();
		draw(framePanel);
		frame.validate();
		frame.repaint();
	}

	private String eventToGamepadCode(Event event) {
		return event.toString() + "--" + event.getComponent().getName();
	}

	private String gamePadKeyDisplayName(String k) {
		return k;
	}

	private String winKeyDisplayName(Integer v) {
		if (v == -1) {
			return "click and type a keyboard key";
		}
		return KeyEvent.getKeyText(v);
	}

	public BindingsProfile bindingsProfile() {
		return bindingsProfile;
	}
}

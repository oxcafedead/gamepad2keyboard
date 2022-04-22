package oxcafedead.g2k;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.java.games.input.Controller;

public class SelectControllerWindow {

	private final JFrame frame;
	private volatile Controller selected = null;

	public SelectControllerWindow() {
		frame = new JFrame("Gamepad 2 Keyboard Converter | Controller");

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
			// okay, let it be the standard one...
		}


		var panel = new JPanel();
		panel.add(Box.createVerticalGlue());
		panel.add(new JLabel("Select controller you want to use:", SwingConstants.LEFT));
		panel.add(Box.createVerticalGlue());
		panel.add(Box.createHorizontalGlue());
		var controllersComboBox = new JComboBox<Controller>();
		controllersComboBox.setSize(450, 50);
		panel.add(controllersComboBox);
		Arrays.stream(Util.getControllers()).sorted((c1, c2) -> {
			var deviceName = c1.getName();
			if (deviceName.toLowerCase(Locale.ROOT).contains("joy")
					|| deviceName.toLowerCase(Locale.ROOT).contains("game")
					|| deviceName.toLowerCase(Locale.ROOT).contains("stick")) {
				return -1;
			}
			return String.CASE_INSENSITIVE_ORDER.compare(c1.getName(), c2.getName());
		}).forEach(controllersComboBox::addItem);
		controllersComboBox.addActionListener(e -> selected = (Controller) ((JComboBox<Controller>) e.getSource()).getSelectedItem());


		frame.add(panel);
		GraphicsConfiguration graphicsConfiguration =
				GraphicsEnvironment.getLocalGraphicsEnvironment()
						.getDefaultScreenDevice()
						.getDefaultConfiguration();
		frame.setVisible(true);
		frame.setResizable(false);
		frame.setSize(500, 100);
		frame.setLocation(
				(graphicsConfiguration.getBounds().width - 500) / 2,
				(graphicsConfiguration.getBounds().height - 100) / 2);
		frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	Controller getController() {
		while (true) {
			if (selected != null) return selected;
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				throw new IllegalStateException();
			}
		}
	}

	public void close() {
		frame.dispose();
	}
}


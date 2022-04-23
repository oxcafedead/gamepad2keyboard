package oxcafedead.g2k;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;

import net.java.games.input.Component;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;

public class Main {

	private static boolean debug = false;
	private static ConfigWindow configWindow;

	public static void main(String[] args) throws AWTException, InterruptedException {

		if (Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("debug"))) {
			debug = true;
		}

		var selectControllerWindow = new SelectControllerWindow();
		var controller = selectControllerWindow.getController();
		selectControllerWindow.close();
		configWindow = new ConfigWindow(controller);

		Robot robot = new Robot();

		Executors.newSingleThreadExecutor().submit(() -> {
			while (true) {
				if (!controller.poll()) {
					JOptionPane.showMessageDialog(null, "Could not connect to selected device!" +
							"\nRestart the program, check the connection or try to select another one.");
					System.exit(1);
				}

				Thread.sleep(20);
				EventQueue queue = controller.getEventQueue();

				Event event = new Event();

				if (!queue.getNextEvent(event)) {
					continue;
				}
				Component comp = event.getComponent();

				debug("event received from device: " + event + " ID === " + comp.getIdentifier());

				Util.addEvent(event);
			}
		});

		while (true) {
			if (Thread.interrupted()) {
				info("Program is shutting down...");
				return;
			}

			var event = Util.nextEvent();
			Collection<Integer> keys = configWindow.bindingsProfile().getCodes(event);
			keys.forEach(key -> {
				configWindow.bindingsProfile().getCodesToRelease(event).forEach(robot::keyRelease);

				if (key == -1) {
					return;
				}

				var identifier = event.getComponent().getIdentifier();
				var isPov = identifier.equals(Component.Identifier.Axis.POV);
				if (isPov && Math.abs(event.getValue()) == 0 || !isPov && Math.abs(event.getValue()) < .8f) {
					debug("reacting: release " + KeyEvent.getKeyText(key));
					robot.keyRelease(key);
				}
				else {
					debug("reacting: pressing " + KeyEvent.getKeyText(key));
					robot.keyPress(key);
				}
			});
		}

	}

	static void debug(String info) {
		if (!debug) return;
		System.out.println("[DEBUG]\t" + info);
	}

	static void info(String info) {
		System.out.println("[INFO]\t" + info);
	}
}

package oxcafedead.g2k;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import de.ralleytn.plugins.jinput.xinput.XInputEnvironmentPlugin;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Event;

public class Util {

	static BlockingDeque<Event> events = new LinkedBlockingDeque<>(1000);

	public static boolean isBindingMode() {
		return bindingMode;
	}

	private static volatile boolean bindingMode;

	static void addEvent(Event e) {
		events.add(e);
	}

	static Event nextEvent() throws InterruptedException {
		while (bindingMode) {
			Thread.onSpinWait();
		}

		while (true) {
			var event = events.take();
			if (bindingMode) {
				events.addFirst(event);
			}
			else {
				return event;
			}
		}
	}

	static Event nextBindingEvent() {
		bindingMode = true;
		Main.debug("** setting isBindingMode = true");
		while (true) {
			try {
				var e = events.take();
				if (Math.abs(e.getValue()) < 0.1) {
					Main.debug("not binding event, skipping");
				}
				else {
					Main.debug("** setting isBindingMode = false");
					bindingMode = false;
					return e;
				}
			}

			catch (InterruptedException e) {
				bindingMode = false;
				return null;
			}
		}
	}

	static Controller[] getControllers() {
		ControllerEnvironment env = new XInputEnvironmentPlugin();

		if (!env.isSupported()) {
			env = ControllerEnvironment.getDefaultEnvironment();
		}

		return env.getControllers();
	}

}

package oxcafedead.g2k;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;

import javax.swing.ImageIcon;
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
                    System.err.println("Could not connect to selected device!");
                    Thread.sleep(100);
                }

                EventQueue queue = controller.getEventQueue();

                Event event = new Event();

                if (!queue.getNextEvent(event)) {
                    Thread.sleep(20);
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
            var bindingsProfile = configWindow.bindingsProfile();
            Collection<Integer> keys = bindingsProfile.getCodes(event);
            keys.forEach(key -> {
                bindingsProfile.getCodesToRelease(event).forEach(robot::keyRelease);

                if (key == -1) {
                    return;
                }

                var identifier = event.getComponent().getIdentifier();
                var isPov = identifier.equals(Component.Identifier.Axis.POV);
                if (isPov && Math.abs(event.getValue()) == 0
                        || !isPov && Math.abs(event.getValue()) < bindingsProfile.analogSensitivity() / 100f) {
                    debug("reacting: release " + KeyEvent.getKeyText(key));
                    robot.keyRelease(key);
                } else {
                    if (!event.getComponent().isAnalog() || Math.abs(event.getValue()) >= bindingsProfile.analogSensitivity() / 100f) {
                        debug("reacting: pressing " + KeyEvent.getKeyText(key));
                        robot.keyPress(key);
                    }
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

    static Image icon() {
        try {
            return new ImageIcon(Path.of("icon.png").toUri().toURL()).getImage();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("no icon");
        }
    }

}

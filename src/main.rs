#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")] // hide console window on Windows in release
use eframe::egui;
use enigo::KeyboardControllable;
use gilrs::{Event, Gilrs};

use std::sync::mpsc::{channel, sync_channel, Receiver, Sender, SyncSender};
use std::thread;

use enigo::{keycodes, Enigo};

// TODO: save/load
// TODO: delete row(s)
#[derive(Debug)]
enum Message {
    UpdateValue(gilrs::EventType),
}
static mut MAPPINGS_SENDER: Option<Sender<Vec<GamepadButtonMapping>>> = None;
static mut MAPPINGS_RECEIVER: Option<Receiver<Vec<GamepadButtonMapping>>> = None;
static mut NEW_GAMEPAD_KEY_SENDER: Option<SyncSender<Message>> = None;
static mut NEW_GAMEPAD_KEY_RECEIVER: Option<Receiver<Message>> = None;

fn main() -> Result<(), eframe::Error> {
    let (new_key_sender, new_key_receiver) = sync_channel(1);
    let (mappings_sender, mappings_receiver) = channel();
    unsafe {
        NEW_GAMEPAD_KEY_SENDER = Some(new_key_sender);
        NEW_GAMEPAD_KEY_RECEIVER = Some(new_key_receiver);
        MAPPINGS_SENDER = Some(mappings_sender);
        MAPPINGS_RECEIVER = Some(mappings_receiver);
    }

    env_logger::init(); // Log to stderr (if you run with `RUST_LOG=debug`).
    let options = eframe::NativeOptions {
        initial_window_size: Some(egui::vec2(640.0, 240.0)),
        ..Default::default()
    };
    thread::spawn(|| listen_for_gamepad_events());

    eframe::run_native(
        "gamepad2keyboard",
        options,
        Box::new(|_cc| Box::<MyApp>::default()),
    )
}

fn listen_for_gamepad_events() -> ! {
    println!("Starting listening for gamepad events...");

    let mut gilrs = Gilrs::new().unwrap();

    // Iterate over all connected gamepads
    for (_id, gamepad) in gilrs.gamepads() {
        println!("{} is {:?}", gamepad.name(), gamepad.power_info());
    }

    let mut active_gamepad = None;

    let mut active_mappings = None;

    let mut enigo = Enigo::new();

    let mut requested_gpkey = false;

    loop {
        // Examine new events
        while let Some(Event { id, event, time }) = gilrs.next_event() {
            println!("{:?} New event from {}: {:?}", time, id, event);
            active_gamepad = Some(id);
            unsafe {
                if let Some(sender) = &NEW_GAMEPAD_KEY_SENDER {
                    if let Some(receiver) = &NEW_GAMEPAD_KEY_RECEIVER {
                        if let Ok(message) = receiver.try_recv() {
                            match message {
                                Message::UpdateValue(new_value) => {
                                    if let gilrs::EventType::Connected = new_value {
                                        println!("requested_gpkey = true;");
                                        requested_gpkey = true;
                                    } else {
                                        dbg!("re-sending new binding to gui thread");
                                        sender.try_send(message);
                                    }
                                }
                            }
                        }
                        if requested_gpkey {
                            println!("sending new binding");
                            sender.try_send(Message::UpdateValue(event));
                            requested_gpkey = false;
                        }
                    }
                }
                if let Some(receiver) = &MAPPINGS_RECEIVER {
                    if let Ok(mappings) = receiver.try_recv() {
                        println!("&MAPPINGS_RECEIVER");
                        active_mappings = Some(mappings);
                    }
                }
            }
            if let Some(mappings) = &active_mappings {
                for mapping in mappings {
                    if mapping.gamepad_button.is_none() || mapping.keyboard_button.is_none() {
                        continue;
                    }
                    println!("testing binding...");
                    for elem in
                        matches_mapping(gilrs.gamepad(id), mappings.to_vec(), &mapping, &event)
                    {
                        let (real_btn, pressed) = elem;
                        if pressed {
                            println!("pressing {:?}", &real_btn);
                            enigo.key_down(real_btn);
                        } else {
                            println!("releasing {:?}", &real_btn);
                            enigo.key_up(real_btn);
                        };
                    }
                }
            }
        }
    }
}

// None if does not match at all, Some(true) if pressed, Some(false) if released
fn matches_mapping(
    gamepad: gilrs::Gamepad,
    mappings: Vec<GamepadButtonMapping>,
    mapping: &GamepadButtonMapping,
    event: &gilrs::EventType,
) -> Vec<(keycodes::Key, bool)> {
    match (mapping.gamepad_button.unwrap().0, event) {
        (
            gilrs::EventType::ButtonPressed(mapping_btn, _)
            | gilrs::EventType::ButtonChanged(mapping_btn, _, _)
            | gilrs::EventType::ButtonReleased(mapping_btn, _),
            gilrs::EventType::ButtonPressed(event_btn, _)
            | gilrs::EventType::ButtonReleased(event_btn, _),
        ) => {
            if *event_btn == mapping_btn {
                if let gilrs::EventType::ButtonPressed(..) = event {
                    return vec![(
                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                        true,
                    )];
                } else {
                    return vec![(
                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                        false,
                    )];
                }
            } else {
                return vec![];
            }
        }
        (
            gilrs::EventType::AxisChanged(mapping_axis, mapping_f32, _),
            gilrs::EventType::AxisChanged(event_axis, event_f32, _),
        ) => {
            let mut vec: std::vec::Vec<(enigo::Key, bool)> = vec![];
            const SENSIVITY: f32 = 0.1f32;
            let axis_list = vec![
                gilrs::Axis::LeftStickX,
                gilrs::Axis::LeftStickY,
                gilrs::Axis::RightStickX,
                gilrs::Axis::RightStickY,
            ]; // TODO: impl all
            for axis in axis_list {
                let axis_data = gamepad.axis_data(axis);
                if axis_data.is_none() {
                    continue; // TODO: key up for all bindings?
                }
                let val = axis_data.unwrap().value();
                for mapping in mappings.to_vec() {
                    if mapping.gamepad_button.is_none() || mapping.keyboard_button.is_none() {
                        continue;
                    }
                    match mapping.gamepad_button.unwrap().0 {
                        gilrs::EventType::AxisChanged(map_axis, map_val, _) => {
                            if axis == map_axis && val.abs() < SENSIVITY {
                                vec.insert(
                                    0,
                                    (
                                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                                        false,
                                    ),
                                );
                            } else if axis == map_axis && map_val.signum() != val.signum() {
                                vec.insert(
                                    0,
                                    (
                                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                                        false,
                                    ),
                                );
                            } else if axis == map_axis && map_val.signum() == val.signum() {
                                vec.insert(
                                    0,
                                    (
                                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                                        true,
                                    ),
                                );
                            }
                        }
                        _ => (),
                    }
                }
            }
            return vec;
            /* if *event_axis == mapping_axis {
                if mapping_f32 >= 0.0 && *event_f32 >= 0.0 {
                    let mut vec = vec![(
                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                        event_f32.abs() > SENSIVITY,
                    )];
                    if let Some(inverted) =
                        get_inverted_mapping(mappings, &mapping.gamepad_button.unwrap())
                    {
                        vec.insert(0, (inverted, false));
                    }
                    return vec;
                } else if mapping_f32 < 0.0 && *event_f32 <= 0.0 {
                    let mut vec = vec![(
                        mapping_to_event_type(&mapping.keyboard_button.unwrap()),
                        event_f32.abs() > SENSIVITY,
                    )];
                    if let Some(inverted) =
                        get_inverted_mapping(mappings, &mapping.gamepad_button.unwrap())
                    {
                        vec.insert(0, (inverted, false));
                    }
                    return vec;
                } else {
                    return vec![];
                }
            } else {
                return vec![];
            } */
        }
        _ => vec![],
    }
}

fn mapping_to_event_type(keyboard_key: &KeyboardButtonWrapper) -> keycodes::Key {
    match keyboard_key {
        KeyboardButtonWrapper::SpecialKey(special_key) => match special_key {
            SpecialKey::Ctrl => keycodes::Key::Control,
            SpecialKey::Shift => keycodes::Key::Shift,
            SpecialKey::Alt => keycodes::Key::Alt,
        },
        KeyboardButtonWrapper::Key(key) => (keycodes::Key::Raw(map_key_to_code(*key).unwrap())), // TODO: add option
    }
}

fn get_inverted_mapping(
    mappings: Vec<GamepadButtonMapping>,
    gamepad_key: &GamepadButtonWrapper,
) -> Option<keycodes::Key> {
    match gamepad_key.0 {
        gilrs::EventType::AxisChanged(axis, value, _) => {
            if value.abs() > 0.0 {
                for mapping_candidate in mappings {
                    if mapping_candidate.gamepad_button.is_none()
                        || mapping_candidate.keyboard_button.is_none()
                    {
                        continue;
                    }
                    match mapping_candidate.gamepad_button.unwrap().0 {
                        gilrs::EventType::AxisChanged(mapping_ax, mapping_val, _) => {
                            if (mapping_ax == axis && mapping_val.signum() != value.signum()) {
                                println!("mapping_val = {}, val = {}", mapping_val, value);
                                return Some(mapping_to_event_type(
                                    &mapping_candidate.keyboard_button.unwrap(),
                                ));
                            }
                        }
                        _ => (),
                    }
                }
            }
        }
        _ => return None,
    }
    return None;
}

struct MyApp {
    changing_gamepad_button: Option<usize>,
    changing_gamepad_button_started: Option<bool>,
    changing_keyboard_button: Option<usize>,
    gamepad_button_mappings: Vec<GamepadButtonMapping>,
}

#[derive(Clone, Copy, Debug)]
struct GamepadButtonMapping {
    gamepad_button: Option<GamepadButtonWrapper>,
    keyboard_button: Option<KeyboardButtonWrapper>,
}

impl Default for MyApp {
    fn default() -> Self {
        Self {
            changing_gamepad_button: None,
            changing_gamepad_button_started: None,
            changing_keyboard_button: None,
            gamepad_button_mappings: Vec::new(),
        }
    }
}

impl MyApp {
    fn add_row(&mut self) {
        self.gamepad_button_mappings.push(GamepadButtonMapping {
            gamepad_button: None,
            keyboard_button: None,
        });
    }
}

#[derive(Debug, Clone, Copy)]
struct GamepadButtonWrapper(gilrs::EventType);

impl GamepadButtonWrapper {
    fn name(self) -> &'static str {
        match self.0 {
            gilrs::EventType::ButtonPressed(button, _)
            | gilrs::EventType::ButtonChanged(button, _, _)
            | gilrs::EventType::ButtonReleased(button, _) => match button {
                gilrs::Button::South => "A", // TODO: see the gamepad type (xbox/ps)
                gilrs::Button::East => "B",
                gilrs::Button::North => "Y",
                gilrs::Button::West => "X",
                gilrs::Button::C => "L",
                gilrs::Button::Z => "R",
                gilrs::Button::LeftTrigger => "L1",
                gilrs::Button::RightTrigger => "R1",
                gilrs::Button::Select => "Select",
                gilrs::Button::Start => "Start",
                gilrs::Button::Mode => "Mode",
                gilrs::Button::DPadLeft => "<",
                gilrs::Button::DPadRight => ">",
                gilrs::Button::DPadDown => "v",
                gilrs::Button::DPadUp => "^",
                _ => "",
            },
            gilrs::EventType::AxisChanged(axis, _, _) => match axis {
                gilrs::Axis::LeftStickX => "LeftStickX",
                gilrs::Axis::LeftStickY => "LeftStickY",
                gilrs::Axis::RightStickX => "RightStickX",
                gilrs::Axis::RightStickY => "RightStickY",
                gilrs::Axis::DPadX => "DPadX",
                gilrs::Axis::DPadY => "DPadY",
                _ => "",
            },
            _ => "",
        }
    }
}

fn map_key_to_code(key: egui::Key) -> Option<u16> {
    match key {
        egui::Key::ArrowDown => Some(40),
        egui::Key::ArrowLeft => Some(37),
        egui::Key::ArrowRight => Some(39),
        egui::Key::ArrowUp => Some(38),
        egui::Key::Backspace => Some(8),
        egui::Key::Delete => Some(46),
        egui::Key::End => Some(35),
        egui::Key::Enter => Some(13),
        egui::Key::Escape => Some(27),
        egui::Key::Home => Some(36),
        egui::Key::Insert => Some(45),
        egui::Key::Num0 => Some(48),
        egui::Key::Num1 => Some(49),
        egui::Key::Num2 => Some(50),
        egui::Key::Num3 => Some(51),
        egui::Key::Num4 => Some(52),
        egui::Key::Num5 => Some(53),
        egui::Key::Num6 => Some(54),
        egui::Key::Num7 => Some(55),
        egui::Key::Num8 => Some(56),
        egui::Key::Num9 => Some(57),
        egui::Key::A => Some(65),
        egui::Key::B => Some(66),
        egui::Key::C => Some(67),
        egui::Key::D => Some(68),
        egui::Key::E => Some(69),
        egui::Key::F => Some(70),
        egui::Key::G => Some(71),
        egui::Key::H => Some(72),
        egui::Key::I => Some(73),
        egui::Key::J => Some(74),
        egui::Key::K => Some(75),
        egui::Key::L => Some(76),
        egui::Key::M => Some(77),
        egui::Key::N => Some(78),
        egui::Key::O => Some(79),
        egui::Key::P => Some(80),
        egui::Key::Q => Some(81),
        egui::Key::R => Some(82),
        egui::Key::S => Some(83),
        egui::Key::T => Some(84),
        egui::Key::U => Some(85),
        egui::Key::V => Some(86),
        egui::Key::W => Some(87),
        egui::Key::X => Some(88),
        egui::Key::Y => Some(89),
        egui::Key::Z => Some(90),
        _ => None,
    }
}

#[derive(Debug, Clone, Copy, Eq, PartialEq, Hash)]
enum KeyboardButtonWrapper {
    SpecialKey(SpecialKey),
    Key(egui::Key),
}

#[derive(Debug, Clone, Copy, Eq, PartialEq, Hash)]
enum SpecialKey {
    Ctrl,
    Shift,
    Alt,
}

impl SpecialKey {
    fn name(self) -> &'static str {
        match self {
            SpecialKey::Ctrl => "Ctrl",
            SpecialKey::Shift => "Shift",
            SpecialKey::Alt => "Alt",
            _ => "Unknown",
        }
    }
}

impl KeyboardButtonWrapper {
    fn name(&self) -> &'static str {
        match self {
            KeyboardButtonWrapper::SpecialKey(key) => key.name(),
            KeyboardButtonWrapper::Key(key) => key.name(),
        }
    }
}

impl eframe::App for MyApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("Mappings: Gamepad key -> Keyboard key");

            let mut i: usize = 0;

            ui.horizontal(|ui| {
                if ui.button("Add Row").clicked() {
                    self.add_row();
                    self.changing_gamepad_button = None;
                    self.changing_keyboard_button = None;
                }
            });

            egui::ScrollArea::vertical().show(ui, |ui| {
                for mapping in &mut self.gamepad_button_mappings {
                    ui.horizontal(|ui| {
                        if ui
                            .button(
                                mapping
                                    .gamepad_button
                                    .map_or("click and record a key", |m: GamepadButtonWrapper| {
                                        m.name()
                                    }),
                            )
                            .clicked()
                        {
                            self.changing_gamepad_button = Some(i);
                            self.changing_gamepad_button_started = Some(true);
                            self.changing_keyboard_button = None;
                        };
                        ui.label("->");
                        let alternatives = [SpecialKey::Ctrl, SpecialKey::Shift, SpecialKey::Alt];
                        let mut special_key_selected = 0usize;
                        if mapping.keyboard_button.is_some() {
                            special_key_selected = match mapping.keyboard_button.unwrap() {
                                KeyboardButtonWrapper::SpecialKey(sk) => match sk {
                                    SpecialKey::Ctrl => 1,
                                    SpecialKey::Shift => 2,
                                    SpecialKey::Alt => 3,
                                },
                                KeyboardButtonWrapper::Key(_) => 0,
                            }
                        }
                        const DEF_SELECTED_TEXT: &str = "[keyboard]";
                        if egui::ComboBox::from_id_source(egui::Id::new(
                            format!("combo_box_{}", i).as_str(),
                        ))
                        .selected_text(if special_key_selected == 0 {
                            DEF_SELECTED_TEXT
                        } else {
                            alternatives[special_key_selected - 1].name()
                        })
                        .show_index(ui, &mut special_key_selected, alternatives.len() + 1, |i| {
                            if i == 0 {
                                return DEF_SELECTED_TEXT.to_string();
                            }
                            alternatives[i - 1].name().to_owned()
                        })
                        .changed()
                        {
                            if let Some(_) = &mut mapping.keyboard_button {
                                if special_key_selected == 0 {
                                    mapping.keyboard_button = None
                                } else {
                                    mapping.keyboard_button =
                                        Some(KeyboardButtonWrapper::SpecialKey(
                                            alternatives[special_key_selected - 1].clone(),
                                        ));
                                }
                            } else {
                                if special_key_selected == 0 {
                                    mapping.keyboard_button = None
                                } else {
                                    mapping.keyboard_button =
                                        Some(KeyboardButtonWrapper::SpecialKey(
                                            alternatives[special_key_selected - 1],
                                        ));
                                }
                            }
                        }
                        if special_key_selected == 0usize {
                            if ui
                                .button(
                                    mapping
                                        .keyboard_button
                                        .map_or("click and record a key", |b| b.name()),
                                )
                                .clicked()
                            {
                                self.changing_keyboard_button = Some(i);
                                self.changing_gamepad_button = None;
                                self.changing_gamepad_button_started = None;
                            };
                        }
                        i += 1;
                    });
                }
            });

            if self.changing_keyboard_button.is_some() {
                if let Some(key) = ctx.input(|i| {
                    let events = &i.events;
                    for ev in events.iter() {
                        println!("some event");
                        if let egui::Event::Key {
                            key,
                            pressed: _,
                            repeat: _,
                            modifiers: _,
                        } = ev
                        {
                            return Some(key.clone());
                        }
                    }
                    return None;
                }) {
                    println!("pressed button! {}", key.name());
                    self.gamepad_button_mappings
                        .get_mut(
                            self.changing_keyboard_button
                                .expect("no keyboard index set!"),
                        )
                        .expect("no keyboard index set!")
                        .keyboard_button = Some(KeyboardButtonWrapper::Key(key));
                    self.changing_keyboard_button = None;
                    update_mappings(&self.gamepad_button_mappings);
                }
            }

            if self.changing_gamepad_button.is_some() {
                let mut value: Option<gilrs::EventType> = None;
                unsafe {
                    if let Some(sender) = &NEW_GAMEPAD_KEY_SENDER {
                        if self.changing_gamepad_button_started.is_some() {
                            dbg!("changing_gamepad_button_started.is_some");
                            sender.try_send(Message::UpdateValue(gilrs::EventType::Connected));
                            self.changing_gamepad_button_started = None;
                        } else if let Some(receiver) = &NEW_GAMEPAD_KEY_RECEIVER {
                            if let Ok(message) = receiver.try_recv() {
                                dbg!("received {}", &message);
                                match message {
                                    Message::UpdateValue(new_value) => {
                                        if let gilrs::EventType::Connected = new_value {
                                            sender.try_send(message);
                                        }
                                        value = match new_value {
                                            gilrs::EventType::AxisChanged(_, _, _)
                                            | gilrs::EventType::ButtonPressed(..)
                                            | gilrs::EventType::ButtonChanged(..)
                                            | gilrs::EventType::ButtonReleased(..) => {
                                                Some(new_value)
                                            }
                                            _ => None,
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ctx.request_repaint();
                if value.is_some() {
                    self.gamepad_button_mappings
                        .get_mut(
                            self.changing_gamepad_button
                                .expect("no keyboard index set!"),
                        )
                        .expect("no keyboard index set!")
                        .gamepad_button = Some(GamepadButtonWrapper(value.unwrap()));
                    self.changing_gamepad_button = None;
                    update_mappings(&self.gamepad_button_mappings);
                }
            }
        });
    }
}

fn update_mappings(gamepad_button_mappings: &Vec<GamepadButtonMapping>) {
    unsafe {
        if let Some(sender) = &MAPPINGS_SENDER {
            sender.send(gamepad_button_mappings.to_vec()).unwrap();
        }
    }
}

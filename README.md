# Granna 📞

**A voice-controlled phone dialer built for my grandmother — because Parkinson's shouldn't take away her independence.**

---

## The Story

My grandmother has Parkinson's disease. The tremors, the stiffness, the way her hands don't always do what she wants them to — it's made something as simple as calling her family feel impossible. She'd look at her phone and feel defeated before she even started. Not because she didn't want to reach out, but because the barrier between her and the people she loves had become too high.

That's not okay.

She is sharp, warm, funny, and full of life. She deserves to pick up the phone and call whoever she wants, whenever she wants — without needing to ask for help, without fumbling through menus, without feeling like her body has taken something from her.

So I built Granna.

---

## What It Does

Granna removes every physical barrier between her and a phone call. Here's the full flow:

1. **She presses a single large button** — a custom-built physical button sitting on her table, no touchscreen required.
2. **Her phone wakes up automatically**, even from sleep or lock screen.
3. A friendly voice asks: *"Who would you like to call?"*
4. **She says the name** — just the name, naturally, the way she'd say it to anyone.
5. The app confirms back: *"You want to call [Name] — is that correct?"*
6. She says **"Yes"** and the call goes through. Or **"No"** to try again.

That's it. One button. One spoken name. One phone call.

No touchscreen. No tiny icons. No menus. No passwords. No frustration.

---

## How It Works

The system has two parts that talk to each other over Bluetooth Low Energy (BLE).

### The Button (ESP32 Hardware)
A small microcontroller — an Aitrip ESP32-S3 — sits inside a physical button on her table. When she presses it, the ESP32 sends a `PRESS` signal over BLE to her Android phone. The LED on the device blinks slowly while scanning for the phone, and stays solid when connected, so it's always easy to see if it's ready.

### The Android App
A foreground service (`ButtonService`) runs silently in the background at all times, listening for the BLE signal. When the button press arrives:

- The screen wakes up immediately
- `DialerActivity` launches full-screen with large, high-contrast text
- A custom recorded voice (warm, clear, unhurried) asks who she'd like to call
- Android's speech recognition listens for a name
- The app fuzzy-matches what it heard against her phone contacts — so "call Mum" or "Mum" or even a partial name will find the right person
- It reads the name back and asks for confirmation using the same warm recorded voice
- On "Yes", the call is placed automatically

The app also starts automatically when the phone reboots, so it's always ready without her needing to do anything.

---

## Project Structure

```
granna_app/
├── app/src/main/java/com/sovereignvoice/granna/
│   ├── MainActivity.kt          # Entry point, permission handling, service launcher
│   ├── DialerActivity.kt        # Full-screen voice dialer UI and logic
│   ├── ButtonService.kt         # BLE GATT server, foreground service, button listener
│   ├── ContactHelper.kt         # Reads phone contacts, fuzzy name matching
│   └── BootReceiver.kt          # Auto-starts the service on device reboot
│
├── app/src/main/res/
│   ├── layout/                  # Large-text, high-contrast UI layouts
│   └── raw/                     # Custom recorded audio prompts
│       ├── who_to_call.mp3      # "Who would you like to call?"
│       ├── call_prefix.mp3      # "You want to call..."
│       └── is_that_correct.mp3  # "...is that correct?"
│
└── esp32_firmware/
    └── granna_button/
        └── granna_button.ino    # ESP32-S3 firmware — BLE client, button handler
```

---

## Hardware

| Part | Details |
|------|---------|
| Microcontroller | Aitrip ESP32-S3 DevKit |
| Button pin | GPIO 13 (internal pull-up, active LOW) |
| LED pin | GPIO 48 (onboard RGB, used as status indicator) |
| BLE role | Client — scans for and connects to the Android app |

**Wiring:** One side of the button to GPIO 13, the other to GND. That's it.

---

## BLE Communication

The Android app advertises as a BLE peripheral named `GrannaListener`. The ESP32 scans for this name, connects, and writes `"PRESS"` to the GATT characteristic when the button is held down.

Both sides share the same UUIDs:
- **Service:** `12345678-1234-1234-1234-123456789abc`
- **Characteristic:** `12345678-1234-1234-1234-123456789abd`

---

## Building & Installing

### Android App
1. Open the project in Android Studio
2. Connect an Android phone (API 26+ / Android 8.0 or higher)
3. Grant all requested permissions (Bluetooth, Contacts, Microphone, Phone)
4. Build and run

### ESP32 Firmware
1. Install the [Arduino IDE](https://www.arduino.cc/en/software) with ESP32 board support
2. Open `esp32_firmware/granna_button/granna_button.ino`
3. Select your ESP32-S3 board
4. Flash using `flash.bat` or the Arduino IDE upload button

---

## Why This Matters

Independence isn't just practical — it's dignity. When someone with Parkinson's can't make a phone call without help, it's not just inconvenient. It chips away at their sense of self. It makes them feel like a burden. It makes them feel like the world is slowly closing in.

Granna is a small piece of technology, but what it gives back is enormous: the ability to reach out to the people you love, on your own terms, in your own time, without asking anyone for help.

She presses a button. She says a name. She hears a familiar voice.

That's everything.

---

*Built with love, for Granna.*

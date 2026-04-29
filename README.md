# Granna-Global 📞

**A voice-controlled phone dialer built for Granna — because she should be able to call who she wants, when she wants.**

---

## The Story

Granna is my grandmother, who has been learning to live with Parkinson's, and it's made using her phone really difficult. She was sold one of those "easy" flip phones marketed to elderly and surprised me how little features it actually had to be "assistive" in any sort of way.... if anything it was the opposite which, kindof pissed me off to be honest. it wasnt user friendly at all, had no voice assist, no functions to actually help with the struggles or problem points that anyone i know who arnt the most tech savey might struggle with, and certainly not for anyone dealing with any sort of dissability which i had thought would be their main purpose (especially as thats how its advertised...) nearly impossible to use.

Anyways, sometimes when I get a call from her, I can tell right away she was trying to call someone else as she will reluctantly tell me so while being obviously (and for no good reason) embarrased, and I can tell how much it discourages her. Then the other day when it happened, she asked me if i didnt mind and had a chance to come over and help her call an old friend she went to school with. When I got there she told me she'd been trying to figure out how to call her for months, and wasn't even sure if she was still alive, which was like... yeah too much, so, Granna-Global!

She is more then capable of talking to who she wants when she wants, her independence, dignity, and confidence that she CAN do whatever, as its 2026, and we have the ability to work around and overcome any sort of difficulties, and when we run into ones we are currently incapable of that we do what we can or put forth the effort towards what we want, as every attempt needs a start. She's super awesome and has alot to say lol so hopefully this can help her say it.

---

## What It Does

One button. Say a name. Make a call.

1. **She presses a single large physical button** on her table — no touchscreen involved
2. **Her phone wakes up** automatically, even from sleep
3. A friendly voice asks: *"Who would you like to call?"*
4. **She says the name** — naturally, however she'd say it
5. The app reads it back: *"You want to call [Name] — is that correct?"*
6. She says **"Yes"** and the call goes through. **"No"** to try again.

No menus. No tiny buttons. No passwords. No frustration.

---

## How It Works

Two parts that talk to each other over Bluetooth Low Energy (BLE).

### The Button (ESP32 Hardware)
An Aitrip ESP32-S3 microcontroller lives inside a physical button on her table. Press it, and the ESP32 sends a `PRESS` signal over BLE to her Android phone. The onboard LED blinks while it's scanning for the phone and goes solid when it's connected — easy to see at a glance if it's ready.

### The Android App
A background service (`ButtonService`) runs silently at all times, listening for the BLE signal. When the button is pressed:

- The screen wakes up
- `DialerActivity` launches full-screen with large, high-contrast text
- A warm recorded voice asks who she'd like to call
- Android speech recognition listens for a name
- The app fuzzy-matches what it heard against her contacts — "Mom", "call Mom", a first name, whatever — and finds the right person
- It reads the name back and asks for confirmation
- She says yes, the call is placed

The service also starts automatically on reboot, so it's always ready without her needing to do anything.

---

## Project Structure

```
granna_global/
├── app/src/main/java/com/sovereignvoice/granna/
│   ├── MainActivity.kt          # Entry point, permissions, service launcher
│   ├── DialerActivity.kt        # Full-screen voice dialer UI and logic
│   ├── ButtonService.kt         # BLE GATT server, foreground service, button listener
│   ├── ContactHelper.kt         # Reads contacts, fuzzy name matching
│   └── BootReceiver.kt          # Auto-starts the service on reboot
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
| LED pin | GPIO 48 (onboard RGB, status indicator) |
| BLE role | Client — scans for and connects to the Android app |

**Wiring:** One side of the button to GPIO 13, the other to GND.

---

## BLE Communication

The Android app advertises as a BLE peripheral named `GrannaListener`. The ESP32 scans for it, connects, and writes `"PRESS"` to the GATT characteristic when the button is held.

Both sides use the same UUIDs:
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

## Granna Global

This is just the start. The goal is to keep building — giving back as much independence as possible to people who deserve it.

*For Granna. She's super awesome.*

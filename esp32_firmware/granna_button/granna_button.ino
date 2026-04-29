/**
 * Granna Button — Aitrip ESP32-S3
 * =================================
 * Connects to the Granna Android app as a BLE client.
 * When the button is pressed, writes "PRESS" to the app's GATT characteristic.
 * The app then launches the dialer with voice recognition.
 *
 * Wiring:
 *   Button: one side → GPIO4, other side → GND
 *   Internal pull-up used — HIGH at rest, LOW when pressed
 *
 * LED: GPIO48 (onboard RGB on Aitrip ESP32-S3 DevKit)
 *   We just use it as a simple indicator via digitalWrite
 */

#include <BLEDevice.h>
#include <BLEClient.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>

#define BUTTON_PIN  13   // Safe GPIO on Aitrip ESP32-S3, not a strapping pin
#define LED_PIN     48    // Aitrip ESP32-S3 onboard LED

// Must match ButtonService.kt
#define SERVICE_UUID     "12345678-1234-1234-1234-123456789abc"
#define CHAR_UUID        "12345678-1234-1234-1234-123456789abd"
#define DEVICE_NAME      "GrannaListener"   // what the Android app advertises as

BLEClient*           pClient     = nullptr;
BLERemoteCharacteristic* pChar   = nullptr;
BLEAdvertisedDevice* targetDevice = nullptr;
bool connected    = false;
bool doConnect    = false;
bool doScan       = true;
uint32_t connectedAt = 0;  // timestamp when connection was established
#define CONNECT_SETTLE_MS 2000  // ignore button for this long after connecting

// Button state
bool     lastReading   = HIGH;
bool     buttonState   = HIGH;
uint32_t pressStart    = 0;
bool     fired         = false;
uint32_t debounceTime  = 0;
uint32_t lowSince      = 0;      // when pin first went LOW
bool     pinStableLow  = false;  // true only after pin held LOW for HOLD_MS

#define DEBOUNCE_MS  150    // ignore bounces shorter than this
#define HOLD_MS      300    // must hold LOW this long to count as a real press

// ── BLE scan callback ─────────────────────────────────────────────────────────
class ScanCallback : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice device) {
        if (device.getName() == DEVICE_NAME) {
            Serial.println("Found GrannaListener app!");
            BLEDevice::getScan()->stop();
            targetDevice = new BLEAdvertisedDevice(device);
            doConnect = true;
            doScan    = false;
        }
    }
};

// ── Connect to app ────────────────────────────────────────────────────────────
bool connectToApp() {
    Serial.println("Connecting to Granna app...");
    pClient = BLEDevice::createClient();

    if (!pClient->connect(targetDevice)) {
        Serial.println("Connection failed");
        return false;
    }
    Serial.println("Connected!");

    BLERemoteService* pService = pClient->getService(SERVICE_UUID);
    if (!pService) {
        Serial.println("Service not found");
        pClient->disconnect();
        return false;
    }

    pChar = pService->getCharacteristic(CHAR_UUID);
    if (!pChar) {
        Serial.println("Characteristic not found");
        pClient->disconnect();
        return false;
    }

    connected   = true;
    connectedAt = millis();
    return true;
}

void setup() {
    Serial.begin(115200);
    delay(3000);  // wait for USB serial to connect before printing anything
    Serial.println("=== Granna Button Starting ===");
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    // 3 blinks = alive
    for (int i = 0; i < 3; i++) {
        digitalWrite(LED_PIN, HIGH); delay(150);
        digitalWrite(LED_PIN, LOW);  delay(150);
    }

    BLEDevice::init("GrannaButton");
    Serial.println("Scanning for Granna app...");
}

void loop() {
    // Reconnect / scan logic
    if (doConnect) {
        if (connectToApp()) {
            Serial.println("Ready — button will trigger dialer");
        } else {
            doScan = true;
        }
        doConnect = false;
    }

    if (doScan) {
        BLEScan* scan = BLEDevice::getScan();
        scan->setAdvertisedDeviceCallbacks(new ScanCallback());
        scan->setActiveScan(true);
        scan->start(5, false);
        doScan = false;
    }

    if (!connected && pClient && !pClient->isConnected()) {
        connected = false;
        doScan    = true;
        Serial.println("Disconnected — rescanning...");
        delay(2000);
    }

    // LED: solid = connected, slow blink = scanning
    if (connected) {
        digitalWrite(LED_PIN, HIGH);
    } else {
        digitalWrite(LED_PIN, (millis() / 500) % 2 == 0 ? HIGH : LOW);
    }

    // Button handling — requires pin held LOW for HOLD_MS to fire
    bool reading = digitalRead(BUTTON_PIN);

    if (reading == LOW) {
        if (lastReading == HIGH) {
            // Just went LOW — start timing
            lowSince = millis();
            pinStableLow = false;
        }
        // Check if held long enough to be a real press
        if (!pinStableLow && (millis() - lowSince) >= HOLD_MS) {
            pinStableLow = true;
            // Ignore spurious press right after BLE connects
            if (!fired && (millis() - connectedAt) > CONNECT_SETTLE_MS) {
                fired = true;
                Serial.println("BUTTON PRESS (confirmed)");
                if (connected && pChar && pChar->canWrite()) {
                    pChar->writeValue("PRESS", false);
                } else {
                    Serial.println("Not connected — rescanning");
                    doScan = true;
                }
            }
        }
    } else {
        // Pin is HIGH — reset everything
        pinStableLow = false;
        fired = false;
        if (lastReading == LOW) {
            Serial.println("button released");
        }
    }

    lastReading = reading;
    delay(10);
}

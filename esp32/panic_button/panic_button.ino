/*
 * Copiloto — Botão de Pânico (ESP32, periférico BLE)
 *
 * Um botão físico ligado a um GPIO do ESP32. Ao ser pressionado (com debounce e
 * confirmação por tempo de pressão), o ESP32 envia uma notificação BLE para o app
 * Android, que dispara o alerta de emergência.
 *
 * Contrato BLE (igual ao app — PanicButtonBle.kt):
 *   Service UUID : 6b2f0001-5f3a-4b3e-9a1e-2d4c6f8a0b01
 *   Char UUID    : 6b2f0002-5f3a-4b3e-9a1e-2d4c6f8a0b02  (NOTIFY)
 *   Nome anunciado: "Copiloto-Panico"
 *   Payload (5 bytes): [0]=0x01 (PANIC) + [1..4]=sequência uint32 little-endian
 *   A sequência incrementa a cada acionamento — o app ignora repetições (anti-duplicação).
 *
 * Hardware:
 *   - Botão entre o GPIO (BUTTON_PIN) e o GND (usa INPUT_PULLUP).
 *   - LED opcional no LED_PIN para indicar conexão.
 *
 * Bibliotecas: usa a "ESP32 BLE Arduino" (inclusa no core do ESP32 no Arduino IDE).
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

static const char* SERVICE_UUID = "6b2f0001-5f3a-4b3e-9a1e-2d4c6f8a0b01";
static const char* CHAR_UUID    = "6b2f0002-5f3a-4b3e-9a1e-2d4c6f8a0b02";
static const char* DEVICE_NAME  = "Copiloto-Panico";

static const int BUTTON_PIN = 0;   // botão para o GND (GPIO0 = BOOT na maioria das devkits)
static const int LED_PIN    = 2;   // LED onboard em muitas placas

// Confiabilidade: debounce + pressão mínima evitam falso acionamento por ruído/toque.
static const unsigned long DEBOUNCE_MS   = 50;
static const unsigned long HOLD_TO_FIRE  = 600;   // segure ~0,6s para disparar
static const unsigned long REARM_MS      = 1500;  // tempo mínimo entre disparos

BLECharacteristic* panicChar = nullptr;
bool deviceConnected = false;
uint32_t seq = 0;

unsigned long pressStart = 0;
bool counted = false;        // já contou este pressionamento?
unsigned long lastFire = 0;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* s) override {
    deviceConnected = true;
    digitalWrite(LED_PIN, HIGH);
  }
  void onDisconnect(BLEServer* s) override {
    deviceConnected = false;
    digitalWrite(LED_PIN, LOW);
    BLEDevice::startAdvertising();   // volta a anunciar para reconectar
  }
};

void sendPanic() {
  if (!deviceConnected || panicChar == nullptr) return;
  seq++;
  uint8_t payload[5];
  payload[0] = 0x01;                       // EVENT_PANIC
  payload[1] = (uint8_t)(seq & 0xFF);      // little-endian
  payload[2] = (uint8_t)((seq >> 8) & 0xFF);
  payload[3] = (uint8_t)((seq >> 16) & 0xFF);
  payload[4] = (uint8_t)((seq >> 24) & 0xFF);
  panicChar->setValue(payload, sizeof(payload));
  panicChar->notify();

  // Pisca o LED para feedback local.
  for (int i = 0; i < 3; i++) { digitalWrite(LED_PIN, LOW); delay(60); digitalWrite(LED_PIN, HIGH); delay(60); }
}

void setup() {
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);
  panicChar = service->createCharacteristic(
      CHAR_UUID,
      BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  panicChar->addDescriptor(new BLE2902());   // CCCD, para o app habilitar notify
  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
}

void loop() {
  bool pressed = (digitalRead(BUTTON_PIN) == LOW);   // LOW = pressionado (pull-up)

  if (pressed) {
    if (pressStart == 0) { pressStart = millis(); counted = false; }
    unsigned long held = millis() - pressStart;
    if (!counted && held >= DEBOUNCE_MS + HOLD_TO_FIRE && (millis() - lastFire) > REARM_MS) {
      counted = true;
      lastFire = millis();
      sendPanic();
    }
  } else {
    pressStart = 0;
    counted = false;
  }

  delay(10);
}

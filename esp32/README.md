# Botão de Pânico — ESP32 (firmware)

Firmware do **botão físico de pânico** instalado atrás do volante. O ESP32 funciona
como periférico **BLE** e, ao pressionar o botão, notifica o app Android, que dispara
o alerta de emergência (origem `BOTAO_PANICO`).

**Fluxo:** Botão físico → ESP32 → BLE → App Android → alerta de segurança.

## Hardware
- 1 ESP32 (qualquer devkit com BLE).
- 1 botão (push-button / NA) ligado entre o **GPIO0** e o **GND** (o firmware usa `INPUT_PULLUP`).
  - Troque `BUTTON_PIN` no sketch se usar outro GPIO.
- (Opcional) LED no **GPIO2** para indicar conexão (aceso = conectado).

## Gravar o firmware
1. Instale o **Arduino IDE** e o core **esp32** (Boards Manager → "esp32" by Espressif).
2. Abra `esp32/panic_button/panic_button.ino`.
3. Selecione a placa (ex.: *ESP32 Dev Module*) e a porta COM.
4. **Upload**. (A lib `BLEDevice` já vem no core do ESP32.)

## Contrato BLE (deve bater com o app — `PanicButtonBle.kt`)
| Item | Valor |
|---|---|
| Service UUID | `6b2f0001-5f3a-4b3e-9a1e-2d4c6f8a0b01` |
| Characteristic | `6b2f0002-5f3a-4b3e-9a1e-2d4c6f8a0b02` (NOTIFY) |
| Nome anunciado | `Copiloto-Panico` |
| Payload (5 bytes) | `[0]=0x01` (PANIC) + `[1..4]` = sequência `uint32` little-endian |

A **sequência** incrementa a cada acionamento. O app guarda a última sequência e
**ignora repetições**, então uma reconexão não re-dispara o último evento.

## Confiabilidade (anti-falso-acionamento)
- **Debounce** de 50 ms + **pressão mínima** de ~0,6 s para disparar (`HOLD_TO_FIRE`).
- **Re-arme** de 1,5 s entre disparos no firmware; o app ainda aplica um cooldown de 30 s.
- Ao desconectar, o ESP32 **volta a anunciar** sozinho para reconectar.

## Testar com o app
1. Grave o firmware e ligue o ESP32.
2. No app: **Ajustes → Botão de pânico (ESP32) → Procurar botão** e toque em `Copiloto-Panico`.
3. O status deve ficar **"Conectado e pronto"**.
4. Toque em **"Testar botão"** e pressione o botão físico: aparece **"✓ Acionamento recebido!"**
   (nesse modo nenhum alerta real é enviado).
5. Fora do modo de teste, pressionar o botão dispara um alerta real na Central de Operações.

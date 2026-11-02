# ESP32 IO board (fog lights, side cameras, blinker sensing)

> **Untested hardware design.** Written without the car or boards in hand. Bench-test everything with a
> 12 V supply, a fused feed and a test lamp before connecting to the vehicle. If unsure, have an auto electrician check it.

## What it does
| Function | How |
|---|---|
| **Blinker sensing** (left / right / hazard) | Opto-isolator reads the left and right lamp circuits -> ESP32 -> `T L 1` / `T R 1` over USB -> arrow overlay on the radio |
| **Side camera on blinker** | Left blinker -> relay CAML on (+ radio opens its video-input app, if enabled in Settings); same for right |
| **Fog lights** | Dock **FOG** button -> `SET FOG 1` -> relay FOG |

The ESP32 is wired to the radio by **USB only**; Bluetooth and Wi-Fi on the ESP32 are unused, so they cannot disturb CarPlay.

## Parts
- ESP32 dev board (your Elegoo ESP-WROOM-32, USB-C) + a data-capable USB cable
- 3-channel 5 V relay module, **opto-isolated**, rated for the load (fog lamps are typically 55 W = ~5 A each -> use >= 10 A relays)
- 2-channel opto-isolator board (e.g. PC817 type, 12 V input side, open-collector output)
- 2 x 10 kohm resistors (pull-ups), inline fuse holders + fuses, 18 AWG wire, fuse tap
- Radio USB port that supplies power and acts as host (try the ones on the back/harness)

## Pins (GPIO numbers)
| GPIO | Role | Connect to |
|---|---|---|
| 26 | FOG relay IN | relay module IN1 |
| 27 | CAML relay IN | relay module IN2 |
| 25 | CAMR relay IN | relay module IN3 |
| 34 | LEFT blinker sense | opto board output 1, plus 10k pull-up to 3V3 |
| 35 | RIGHT blinker sense | opto board output 2, plus 10k pull-up to 3V3 |
| 5V / VIN | relay module VCC | |
| GND | relay + opto GND | common ground with the radio USB |

GPIO 34/35 are input-only and have **no internal pull-up**: the external 10k to 3V3 is required.
The opto output pulls the pin LOW while that side's lamp circuit is energised.
All three relay outputs start **OFF** at power-up. The firmware keeps USB DTR/RTS low so the radio opening the port does not reset the board (a reset would drop the fog lights for a moment).

```
            12V lamp feed (L)  --[opto IN, via its resistor]-- chassis GND
 3V3 --10k--+-- GPIO34 ------- opto OUT (collector)   (emitter -> ESP GND)

 Battery+ --fuse(15A)-- relay COM ; relay NO --> fog lamp (+) ; fog lamp (-) -> chassis GND
 ESP GPIO26 -> relay IN1 ; relay VCC 5V ; relay GND -> ESP GND
```

## Safety rules
- **Never connect 12 V (or the lamp wires) to an ESP32 pin.** Only through the opto-isolator.
- Fuse every power feed **at the battery end**. Fog lamps get their own fuse and relay; do not power them from the ESP32 board.
- Tap the blinker signal at a lamp connector or back-probe the harness; do not cut airbag (yellow) wiring or anything near it. Verify polarity and that the circuit is the *lamp* feed (flashing), not the flasher control wire.
- Check local law: fog lights are normally only for low-visibility use, and side cameras must not be the driver's only reference. Keep the manual FOG button state visible.
- Keep the board in a dry spot away from heat.

## Flash the firmware
1. Install Arduino IDE 2.x, then *Boards Manager -> "esp32" by Espressif*.
2. Board: **ESP32 Dev Module**. Select the port and upload `esp32/ridgeline_io/ridgeline_io.ino`.
   (Install the CP210x driver on a PC if the board does not appear.)
3. Open the Serial Monitor at **115200**: you should see `HELLO ridgeline-io 1` every 3 s.
4. Type `SET FOG 1` then `SET FOG 0`: the relay should click and the monitor answers `ACK FOG 1/0`. Short GPIO34 to GND: `T L 1` appears, then `T L 0` about 1.2 s after you stop.
5. If the relay is inverted, flip `RELAY_ACTIVE_LOW` in the sketch.

## On the radio
1. Plug the ESP32 into the radio USB port. Allow the USB permission dialog and tick "Use by default".
2. Honda H -> Settings: the line **ESP32: connected (ridgeline-io v1)** confirms the link.
3. The **FOG** button now sends `SET FOG 1/0` automatically (Settings -> Configure buttons shows it as "ESP32 output / FOG").
4. Optional: Settings -> tick **Show video input while left/right blinker is on**, and set the video input app first.

## Open points
- **One AV input for two cameras:** the radio has a single video input. CAML/CAMR relays can power two cameras, but combining them into one video feed needs a video switcher (or a quad/dual-camera box) that the relays drive. Tell me which camera system you're using and I'll match the logic.
- Hazard lights suppress the camera behaviour on purpose.

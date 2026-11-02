// Ridgeline IO - ESP32 (ESP-WROOM-32 dev board) <-> radio over USB serial, 115200 8N1, ASCII lines.
//
//  Radio -> ESP32          ESP32 -> radio
//  SET FOG|CAML|CAMR 0|1   HELLO ridgeline-io 1        (every 3 s)
//  AUTOCAM 0|1             T L|R 1|0                    (blinker active/ended; repeated every 500 ms while active)
//  GET                     ACK <channel> <0|1>
//  PING                    STATE FOG=0 CAML=0 CAMR=0 L=0 R=0 AUTOCAM=1
//                          PONG
//
// Everything powers up OFF. 12 V signals NEVER go to a GPIO directly: use an opto-isolator board.
// See ../docs/esp32.md for wiring.

// ---- pins (avoid strapping/boot-noisy pins 0,2,5,12,14,15) ----
const int PIN_FOG  = 26;   // relay IN: fog lights
const int PIN_CAML = 27;   // relay IN: left side camera power/select
const int PIN_CAMR = 25;   // relay IN: right side camera power/select
const int PIN_L    = 34;   // blinker sense, LEFT  (input-only pin: needs external 10k pull-up to 3V3)
const int PIN_R    = 35;   // blinker sense, RIGHT (same)

const bool RELAY_ACTIVE_LOW = true;   // most 5 V relay boards: LOW = relay ON. Set false if yours is the opposite.
const uint32_t HOLD_MS = 1200;        // blinker counts as "on" until this long after the last flash
const uint32_t REPEAT_MS = 500;

bool fog = false, camL = false, camR = false, autoCam = true;
uint32_t lastL = 0, lastR = 0, lastRepeat = 0, lastHello = 0;
bool actL = false, actR = false;
String rx;

void relay(int pin, bool on) { digitalWrite(pin, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW); }

void initRelay(int pin) {
  pinMode(pin, OUTPUT);
  relay(pin, false);
}

void ack(const char* name, bool v) { Serial.print("ACK "); Serial.print(name); Serial.print(' '); Serial.println(v ? 1 : 0); }

void state() {
  Serial.printf("STATE FOG=%d CAML=%d CAMR=%d L=%d R=%d AUTOCAM=%d\n", fog, camL, camR, actL, actR, autoCam);
}

void setChannel(const String& ch, bool v) {
  if (ch == "FOG")       { fog  = v; relay(PIN_FOG,  v); ack("FOG", v); }
  else if (ch == "CAML") { camL = v; relay(PIN_CAML, v); ack("CAML", v); }
  else if (ch == "CAMR") { camR = v; relay(PIN_CAMR, v); ack("CAMR", v); }
}

void handleLine(String s) {
  s.trim(); s.toUpperCase();
  if (s.startsWith("SET ")) {
    int sp = s.indexOf(' ', 4);
    if (sp > 0) setChannel(s.substring(4, sp), s.substring(sp + 1).toInt() != 0);
  } else if (s.startsWith("AUTOCAM ")) { autoCam = s.substring(8).toInt() != 0; ack("AUTOCAM", autoCam); }
  else if (s == "GET")  state();
  else if (s == "PING") Serial.println("PONG");
}

void reportBlinker(char side, bool on) { Serial.printf("T %c %d\n", side, on ? 1 : 0); }

void setup() {
  initRelay(PIN_FOG); initRelay(PIN_CAML); initRelay(PIN_CAMR);
  pinMode(PIN_L, INPUT);
  pinMode(PIN_R, INPUT);
  Serial.begin(115200);
  rx.reserve(64);
}

void loop() {
  uint32_t now = millis();

  // blinker sense: opto output pulls the pin LOW while that side's lamp circuit is energised
  if (digitalRead(PIN_L) == LOW) lastL = now ? now : 1;
  if (digitalRead(PIN_R) == LOW) lastR = now ? now : 1;
  bool newL = lastL && (now - lastL < HOLD_MS);
  bool newR = lastR && (now - lastR < HOLD_MS);

  if (newL != actL) {
    actL = newL; reportBlinker('L', actL); lastRepeat = now;
    if (autoCam && !actR) { camL = actL; relay(PIN_CAML, camL); }   // hazards (both) -> no camera
  }
  if (newR != actR) {
    actR = newR; reportBlinker('R', actR); lastRepeat = now;
    if (autoCam && !actL) { camR = actR; relay(PIN_CAMR, camR); }
  }
  if ((actL || actR) && now - lastRepeat >= REPEAT_MS) {
    lastRepeat = now;
    if (actL) reportBlinker('L', true);
    if (actR) reportBlinker('R', true);
  }

  if (now - lastHello >= 3000) { lastHello = now; Serial.println("HELLO ridgeline-io 1"); }

  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') { handleLine(rx); rx = ""; }
    else if (rx.length() < 60) rx += c;
  }
}

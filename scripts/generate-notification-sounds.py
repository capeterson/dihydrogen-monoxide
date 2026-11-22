#!/usr/bin/env python3
"""Render the app's original water-drop and wooden chimes as PCM WAV files."""

import math
import struct
import wave
from pathlib import Path

RATE = 22050
DEST = Path(__file__).resolve().parents[1] / "core/reminders/src/main/res/raw"


def write_sound(name, samples):
    with wave.open(str(DEST / f"{name}.wav"), "wb") as output:
        output.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        output.writeframes(b"".join(samples))


def render(name, notes, duration):
    samples = []
    for i in range(int(RATE * duration)):
        t = i / RATE
        value = 0.0
        for start, frequency, strength in notes:
            age = t - start
            if age < 0:
                continue
            attack = min(1.0, age / 0.018)
            envelope = attack * math.exp(-age * 4.5)
            # A soft fundamental with quiet, quickly decaying wooden overtones.
            tone = math.sin(2 * math.pi * frequency * age)
            tone += 0.25 * math.sin(2 * math.pi * frequency * 2.01 * age) * math.exp(-age * 5)
            tone += 0.10 * math.sin(2 * math.pi * frequency * 3.9 * age) * math.exp(-age * 9)
            value += strength * envelope * tone
        fade = min(1.0, max(0.0, (duration - t) / 0.08))
        samples.append(struct.pack("<h", int(max(-1, min(1, value * fade)) * 32767)))
    write_sound(name, samples)


def render_droplet():
    samples = []
    duration = 0.85
    for i in range(int(RATE * duration)):
        t = i / RATE
        # A rounded, upward-pitched plink followed by a softer water resonance.
        phase = 2 * math.pi * (640 * t - 170 / 18 * (1 - math.exp(-18 * t)))
        attack = min(1.0, t / 0.008)
        body = math.sin(phase) * math.exp(-t * 8)
        ripple = 0.22 * math.sin(2 * math.pi * 1120 * t) * math.exp(-t * 15)
        fade = min(1.0, max(0.0, (duration - t) / 0.08))
        value = 0.29 * attack * (body + ripple) * fade
        samples.append(struct.pack("<h", int(max(-1, min(1, value)) * 32767)))
    write_sound("core_reminders_reminder_chime", samples)


DEST.mkdir(parents=True, exist_ok=True)
render_droplet()
render("core_reminders_summary_chime", [(0.0, 392.00, 0.20), (0.17, 523.25, 0.17), (0.34, 659.25, 0.14)], 1.30)
render("core_reminders_backup_chime", [(0.0, 440.00, 0.20), (0.23, 392.00, 0.17)], 1.15)

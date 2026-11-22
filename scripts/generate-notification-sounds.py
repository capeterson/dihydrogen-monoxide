#!/usr/bin/env python3
"""Render the app's original, gently struck wooden/water chimes as PCM WAV files."""

import math
import struct
import wave
from pathlib import Path

RATE = 22050
DEST = Path(__file__).resolve().parents[1] / "core/reminders/src/main/res/raw"


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
    with wave.open(str(DEST / f"{name}.wav"), "wb") as output:
        output.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        output.writeframes(b"".join(samples))


DEST.mkdir(parents=True, exist_ok=True)
render("reminder_chime", [(0.0, 523.25, 0.24), (0.20, 659.25, 0.20)], 1.15)
render("summary_chime", [(0.0, 392.00, 0.20), (0.17, 523.25, 0.17), (0.34, 659.25, 0.14)], 1.30)
render("backup_chime", [(0.0, 440.00, 0.20), (0.23, 392.00, 0.17)], 1.15)

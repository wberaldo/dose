#!/usr/bin/env python3
"""Generate the six original Dose tones as deterministic mono PCM, without dependencies."""
import math
import pathlib
import struct
import wave

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
RATE = 24000


def clip(name, duration, events):
    samples = [0.0] * round(duration * RATE)
    for start, length, frequency, amplitude, kind in events:
        for i in range(round(length * RATE)):
            at = round(start * RATE) + i
            if at >= len(samples):
                break
            t = i / RATE
            attack = min(1.0, t / 0.008)
            tail = min(1.0, max(0.0, length - t) / 0.06)
            decay = math.exp(-t * (4 if kind == 'bell' else 2))
            phase = 2 * math.pi * (frequency * t + (80 * t * t if kind == 'drop' else 0))
            value = math.sin(phase)
            if kind == 'bell':
                value += 0.24 * math.sin(phase * 2.01) * math.exp(-t * 3)
                value += 0.1 * math.sin(phase * 3.98) * math.exp(-t * 5)
            elif kind == 'pulse':
                value += 0.2 * math.sin(phase * 2)
            samples[at] += amplitude * attack * tail * decay * value
    peak = max(abs(x) for x in samples)
    scale = min(1.0, 0.72 / max(peak, 0.001))
    data = b''.join(struct.pack('<h', round(x * scale * 32767)) for x in samples)
    with wave.open(str(ROOT / ('dose_' + name + '.wav')), 'wb') as out:
        out.setparams((1, 2, RATE, len(samples), 'NONE', 'not compressed'))
        out.writeframes(data)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    clip('suave', 4.2, [(0, 1.4, 523.25, .24, 'bell'), (.55, 1.4, 659.25, .23, 'bell'),
                       (1.1, 1.6, 783.99, .22, 'bell')])
    clip('gotas', 3.2, [(at, .36, pitch, .42, 'drop') for at, pitch in
                       [(0, 1046.5), (.32, 783.99), (.64, 1174.66), (1.4, 1046.5), (1.72, 783.99)]])
    clip('sinos', 4.4, [(0, 1.5, 783.99, .39, 'bell'), (.65, 1.5, 1046.5, .35, 'bell'),
                       (1.5, 1.7, 1318.51, .31, 'bell')])
    clip('pulso', 3.6, [(at, .3, 440, .5, 'pulse') for at in [0, .4, .8, 1.8, 2.2, 2.6]])
    clip('digital', 3.6, [(base + step * .17, .14, pitch, .42, 'sine')
                         for base in [0, 1.55]
                         for step, pitch in enumerate([880, 1174.66, 1318.51, 1174.66])])
    clip('classico', 3.8, [(base + step * .18, .16, 880 if step % 2 == 0 else 660, .48, 'pulse')
                          for base in [0, 1.8] for step in range(6)])


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Generate all audio assets for Sky Dreams (WAV, 44100 Hz, 16-bit).

Writes into app/src/main/res/raw/: sfx_jump, sfx_star, sfx_powerup, sfx_hit,
sfx_gameover, sfx_click, sfx_best and the looping background music music_loop.
"""
import math
import os
import random
import struct
import wave

SR = 44100
OUT = os.path.join("app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)


def write_wav(name, samples, vol=1.0):
    peak = max(1e-9, max(abs(s) for s in samples))
    k = min(1.0, 0.95 * vol / peak)
    data = b"".join(struct.pack("<h", max(-32767, min(32767, int(s * k * 32767)))) for s in samples)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


def env(t, a, d, sus=0.0, rel=0.0, total=None):
    total = total if total is not None else a + d + rel
    if t < a:
        return t / a if a > 0 else 1.0
    if t < a + d:
        f = (t - a) / d
        return sus + (1 - sus) * (1 - f) ** 1.5
    return max(0.0, sus * (1 - (t - a - d) / max(1e-9, rel))) if rel > 0 else sus


def sine(f, t):
    return math.sin(2 * math.pi * f * t)


def square(f, t, duty=0.5):
    return 1.0 if (t * f) % 1.0 < duty else -1.0


def tri(f, t):
    return 2.0 * abs(2.0 * ((t * f) % 1.0) - 1.0) - 1.0


def tone(name, dur, freq_fn, wave_fn=sine, a=0.005, d=None, sus=0.0, rel=None, vol=0.9,
         vibrato=0.0, vibf=6.0):
    d = d if d is not None else dur * 0.4
    rel = rel if rel is not None else max(0.0, dur - a - d)
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        f = freq_fn(t) if callable(freq_fn) else freq_fn
        if vibrato:
            f *= 1.0 + vibrato * math.sin(2 * math.pi * vibf * t)
        out.append(wave_fn(f, t) * env(t, a, d, sus, rel, dur) * vol)
    write_wav(name, out)


def chord_seq(name, notes, each, wave_fn=sine, vol=0.85):
    out = []
    for f, m in notes:
        n = int(SR * each * m)
        for i in range(n):
            t = i / SR
            e = env(t, 0.008, each * m * 0.3, 0.6, each * m * 0.6, each * m)
            out.append(wave_fn(f, t) * e * vol)
    write_wav(name, out)


# ---------------------------------------------------------------- SFX
def gen_sfx():
    # Jump: rising "boing" sweep
    def jump(t):
        return 320 + 480 * (t / 0.18) ** 0.7
    tone("sfx_jump", 0.22, jump, square, a=0.003, d=0.10, sus=0.35, rel=0.10, vol=0.5,
         vibrato=0.02, vibf=14)

    # Star pickup: sparkling arpeggio up (C6-E6-G6-C7)
    chord_seq("sfx_star", [(1046.5, 1), (1318.5, 1), (1568.0, 1), (2093.0, 1.4)], 0.055, tri, vol=0.5)

    # Power-up: fast rising major arpeggio
    chord_seq("sfx_powerup",
              [(523.25, 1), (659.25, 1), (783.99, 1), (1046.5, 1), (1318.5, 1.2)],
              0.07, tri, vol=0.55)

    # Hit: descending "womp"
    tone("sfx_hit", 0.28, lambda t: 440 * (1 - 0.55 * t / 0.28), square,
         a=0.002, d=0.08, sus=0.2, rel=0.18, vol=0.5)

    # Game over: sad descending phrase
    chord_seq("sfx_gameover", [(659.25, 1), (523.25, 1), (392.0, 2.2)], 0.16, sine, vol=0.6)

    # UI click: tiny tick
    tone("sfx_click", 0.05, 1800, sine, a=0.001, d=0.02, sus=0.0, rel=0.02, vol=0.4)

    # New record fanfare
    chord_seq("sfx_best",
              [(523.25, 1), (659.25, 1), (783.99, 1), (1046.5, 1), (1318.5, 1), (1568.0, 2)],
              0.09, tri, vol=0.55)


# ---------------------------------------------------------------- MUSIC
def gen_music():
    bpm = 118
    beat = 60 / bpm
    eighth = beat / 2

    chords = [
        (220.00, 261.63, 329.63),   # Am
        (174.61, 220.00, 261.63),   # F
        (130.81, 164.81, 196.00),   # C
        (196.00, 246.94, 293.66),   # G
    ]
    bass_roots = [110.0, 87.31, 65.41, 98.0]

    melody = [
        (880.0, 1), (987.77, 1), (880.0, 1), (659.25, 1),
        (783.99, 1), (659.25, 1), (587.33, 2),
        (659.25, 1), (783.99, 1), (880.0, 1), (1046.5, 1),
        (987.77, 1), (783.99, 1), (659.25, 2),
    ]

    bar_beats = 8
    total_dur = bar_beats * 8 * beat  # 2 passes over the 4 chords
    n_total = int(SR * total_dur)
    buf = [0.0] * n_total

    def add_tone(start, dur, freq, vol, wave="tri", decay_pow=1.2):
        n = int(SR * dur)
        s0 = int(SR * start)
        for i in range(n):
            idx = s0 + i
            if idx >= n_total:
                break
            t = i / SR
            e = min(1.0, t / 0.01) * (1 - t / dur) ** decay_pow
            if wave == "tri":
                v = tri(freq, t)
            else:
                v = sine(freq, t)
            buf[idx] += v * e * vol

    for rep in range(2):
        for ci, ch in enumerate(chords):
            start = (rep * 4 + ci) * bar_beats * beat
            # soft pad
            for f in ch:
                add_tone(start, bar_beats * beat, f, 0.05, "sine", decay_pow=0.2)
            # arpeggio sparkle
            arp = [ch[0] * 2, ch[2] * 2, ch[1] * 2, ch[2] * 2] * (bar_beats // 2)
            for k, f in enumerate(arp):
                add_tone(start + k * eighth, eighth * 0.9, f, 0.07, "tri", decay_pow=1.0)
            # bass
            root = bass_roots[ci]
            for k in range(bar_beats * 2):
                f = root if k % 4 != 3 else root * 2
                add_tone(start + k * eighth, eighth * 0.85, f, 0.24, "sine", decay_pow=0.7)

            # drums + hats
            for beat_i in range(bar_beats):
                t0 = start + beat_i * beat
                if beat_i in (0, 4):
                    n = int(SR * 0.12)
                    s0 = int(SR * t0)
                    for i in range(n):
                        idx = s0 + i
                        if idx >= n_total:
                            break
                        t = i / SR
                        f = 150 * math.exp(-t * 18) + 42
                        buf[idx] += math.sin(2 * math.pi * f * t) * (1 - t / 0.12) ** 2 * 0.5
                if beat_i in (2, 6):
                    n = int(SR * 0.09)
                    s0 = int(SR * t0)
                    z = 0.0
                    for i in range(n):
                        idx = s0 + i
                        if idx >= n_total:
                            break
                        t = i / SR
                        alpha = 1 - math.exp(-2 * math.pi * 3500 / SR)
                        z += alpha * (random.uniform(-1, 1) - z)
                        buf[idx] += z * (1 - t / 0.09) ** 1.5 * 0.42
                for h in range(2):
                    t0h = t0 + h * eighth
                    n = int(SR * 0.03)
                    s0 = int(SR * t0h)
                    for i in range(n):
                        idx = s0 + i
                        if idx >= n_total:
                            break
                        t = i / SR
                        buf[idx] += random.uniform(-1, 1) * (1 - t / 0.03) ** 2 * 0.05

    # melody
    for rep in range(2):
        t_cursor = rep * 4 * bar_beats * beat
        for f, b in melody:
            add_tone(t_cursor, b * beat * 0.95, f, 0.17, "tri", decay_pow=1.1)
            t_cursor += b * beat

    # normalize + tiny fade at loop point
    peak = max(abs(v) for v in buf)
    if peak > 0:
        g = 0.92 / peak
        buf = [v * g for v in buf]
    fade = int(SR * 0.02)
    for i in range(fade):
        buf[-1 - i] *= i / fade

    write_wav("music_loop", buf)


if __name__ == "__main__":
    gen_sfx()
    gen_music()
    print("Audio assets generated into", OUT)

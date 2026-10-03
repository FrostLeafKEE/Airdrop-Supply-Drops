"""Original procedural sound design for Airdrop. LGPL-3.0-only, FrostLeafKEE."""
from pathlib import Path
import sys
import json

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.audio-tools'))
import numpy as np
import soundfile as sf

RATE = 44100
rng = np.random.default_rng(20260911)
OUT = ROOT / 'src/main/resources/assets/airdrop_supply_drops/sounds'
OUT.mkdir(parents=True, exist_ok=True)
report = {}

def noise(seconds, low, high):
    n = round(seconds * RATE)
    freq = np.fft.rfftfreq(n, 1 / RATE)
    spectrum = np.fft.rfft(rng.normal(size=n))
    spectrum *= (1 - np.exp(-(freq / low) ** 2)) / np.sqrt(1 + (freq / high) ** 6)
    value = np.fft.irfft(spectrum, n)
    return value / max(np.std(value), 1e-8)

def save(name, value, peak, loop=False):
    value -= value.mean()
    if not loop:
        fade = min(220, len(value) // 10)
        value[:fade] *= np.linspace(0, 1, fade)
        value[-fade:] *= np.linspace(1, 0, fade)
    value *= peak / max(np.max(np.abs(value)), 1e-8)
    path = OUT / (name + '.ogg')
    sf.write(path, value, RATE, format='OGG', subtype='VORBIS')
    decoded, rate = sf.read(path)
    assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < 1 and np.sqrt(np.mean(decoded ** 2)) > 0.01
    report[name] = dict(seconds=len(decoded) / rate, channels=1, sample_rate=rate,
                        peak=float(np.max(np.abs(decoded))), rms=float(np.sqrt(np.mean(decoded ** 2))),
                        loop_boundary_step=float(abs(decoded[-1] - decoded[0])) if loop else None)

# Integer-cycle tones and FFT-shaped periodic noise give seamless sustained loops.
t = np.arange(8 * RATE) / RATE
phase = 2 * np.pi * 45 * t + 0.10 * np.sin(2 * np.pi * 0.5 * t)
engine = sum(np.sin(phase * harmonic) / harmonic ** 1.35 for harmonic in range(1, 15))
engine += 0.65 * np.sin(2 * np.pi * 46 * t) + 0.18 * np.sin(2 * np.pi * 720 * t)
engine += 0.38 * noise(8, 25, 650) * (0.65 + 0.35 * np.cos(phase * 2))
save('aircraft_engine', engine, 0.78, loop=True)
wind = noise(8, 90, 2200) * (0.7 + 0.14 * np.sin(2 * np.pi * 0.25 * t) + 0.07 * np.sin(2 * np.pi * 0.875 * t))
wind += 0.12 * noise(8, 400, 4500) * (0.5 + 0.5 * np.sin(2 * np.pi * 3 * t))
save('descent_wind', wind, 0.5, loop=True)
t = np.arange(round(1.5 * RATE)) / RATE
fabric = noise(1.5, 180, 4200)
deploy = fabric * (0.35 * np.exp(-((t - 0.2) / 0.13) ** 2) + 0.7 * np.exp(-((t - 0.48) / 0.09) ** 2))
deploy += 0.9 * np.sin(2 * np.pi * (95 * t - 15 * t * t)) * np.exp(-np.maximum(t - 0.43, 0) * 12) * (t >= 0.43)
deploy += 0.15 * fabric * np.exp(-np.maximum(t - 0.6, 0) * 3) * (t >= 0.6) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * t))
save('parachute_open', deploy, 0.8)
t = np.arange(RATE) / RATE
impact = (np.sin(2 * np.pi * 78 * t) + 0.45 * np.sin(2 * np.pi * 167 * t)) * np.exp(-18 * t)
impact += 0.65 * noise(1, 250, 3800) * np.exp(-45 * t)
impact += 0.18 * noise(1, 700, 6200) * np.exp(-np.maximum(t - 0.075, 0) * 28) * (t >= 0.075)
save('crate_land', impact, 0.85)
t = np.arange(round(0.65 * RATE)) / RATE
flare = noise(0.65, 140, 5000) * np.exp(-10 * t) + 0.3 * np.sin(2 * np.pi * 120 * t) * np.exp(-35 * t)
save('flare_launch', flare, 0.7)
(ROOT / 'audio/validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
print(json.dumps(report, indent=2))

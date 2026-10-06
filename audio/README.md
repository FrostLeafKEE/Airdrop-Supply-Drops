# Sound sources

Original procedural synthesis by FrostLeafKEE, with no third-party recordings or vanilla audio samples. Outputs are 44.1 kHz mono OGG Vorbis under `src/main/resources/assets/airdrop_supply_drops/sounds/`.

| File | Sound | Duration |
| --- | --- | --- |
| `aircraft_engine.ogg` | Engine, propeller, and airflow | 8-second loop |
| `descent_wind.ogg` | Low gusts and gentle canopy flutter | 8-second loop |
| `parachute_open.ogg` | Canopy inflation and rope tension | 1.5 seconds |
| `smoke_hiss.ogg` | Soft hiss while a landed crate emits smoke | 8-second loop |
| `crate_land.ogg` | Wooden impact and metal rattle | 1 second |
| `flare_launch.ogg` | Flare launch and jet burst | 0.65 seconds |

Rebuild from the repository root:

```sh
python -m pip install --target .audio-tools numpy soundfile
python audio/build_sounds.py
```

The script uses a fixed random seed. `validation.json` records decoded duration, channels, peak/RMS levels, and loop boundaries. These checks verify files, not their in-game mix. Python and the libraries are only required to recreate assets.

Sources and sounds use LGPL-3.0-only. See [LICENSE](../LICENSE).

Airflow follows parachute opening and stops at landing. Smoke hiss follows the server smoke flag, fades to silence at 32 blocks, and stops when smoke expires, supplies are emptied, the crate is destroyed or unloaded, or the player leaves the world. Airborne smoke configuration does not enable hiss during descent.

To rebuild only these assets: `python audio/build_sounds.py --only descent_wind parachute_open smoke_hiss`.

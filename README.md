# PocketDex

**English** | [中文](README.zh-CN.md)

A Pokédex app for foldable phones (built for the OPPO Find N2). Unfolded, the two halves of the screen are the two pages of the Pokédex. The left page identifies a Pokémon with the camera. The right page shows base stats, a number keypad and buttons.

## Features

- **On-device recognition.** A ViT model runs on the phone and covers all 1025 Pokémon up to Generation IX. No network, no cost.
- **Claude check (optional).** When the on-device model is less than 50% sure, the app can send the photo to Claude for a second look. You can also tap "Ask Claude" yourself. This needs an API key in Settings.
- **Offline Pokédex data.** Name, category, height and weight, types, abilities (including hidden ones), base stats with a radar chart, Pokédex entry, type matchups, evolution chain with conditions, level-up moves, catch rate, gender ratio and egg groups.
- **English / 中文.** Switch in Settings. The UI, the Pokédex text and the read-aloud voice all follow the setting. On first launch it follows the phone's system language.
- Identify from a **photo**, turn on the **light**, **zoom** with the D-pad, and have the blue lens **read** the entry aloud with the system voice.
- **Artwork is downloaded once.** No Pokémon images ship with the app or this repo. On first launch the app fetches the official artwork for all 1025 Pokémon from [PokeAPI](https://github.com/PokeAPI/sprites) in the background (about 130 MB, on Wi-Fi only) and keeps a 256 px copy on the phone (about 12 MB). The Pokémon on screen is fetched right away on any network. After that everything works offline.
- Works on the outer screen when folded: the two pages stack vertically.

## Controls

| Control | What it does |
|---|---|
| Green SCAN | Opens the camera. Press again while the camera is on to identify what's in the viewfinder |
| Small red PHOTO | Identify a picture from your photos |
| Small blue LIGHT | Turns the flashlight on or off while the camera is on |
| D-pad | ← → previous / next Pokémon · ↑ ↓ switch page (zoom while the camera is on) · hold to repeat |
| Keypad + SEARCH | Look up a National Dex number |
| CANCEL | Clear the input, or go back one step |
| Yellow RANDOM | Show a random Pokémon |
| Big blue lens | Read the current entry aloud, or stop |
| Black SETUP | Language, Claude API key and model, auto read-aloud, beeps |

## Build and install

You need JDK 17 and the Android SDK (`sdk.dir` in `local.properties`).

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleRelease          # ~130 MB, signed with the local debug key
adb install -r app/build/outputs/apk/release/app-release.apk
```

`assembleDebug` also works, but its APK is larger. Debug builds have a test hook that runs recognition on an image in the app's private storage:

```bash
adb push pikachu.jpg /data/local/tmp/t.jpg
adb shell run-as app.pocketdex cp /data/local/tmp/t.jpg files/test.jpg
adb shell am start -n app.pocketdex/.MainActivity --es test_image test.jpg
```

First enable USB debugging on the phone: Settings → About device → Version → tap "Build number" 7 times → go back to Settings → Developer options → turn on "USB debugging". The first time you install over USB, ColorOS asks you to confirm on the phone.

Enter the API key in the app's Settings, or put `ANTHROPIC_API_KEY=...` in `local.properties` to build it into your own APK. Don't share an APK built with a key.

**ColorOS shows the app in a narrow window?** ColorOS opens new apps in a compatibility window on the Find N2's inner screen. Tap the button at the top left of the screen, then tap "Restart" to switch the app to full screen. ColorOS remembers this per app.

On the Find N2 the big blue lens sits right on the front camera hole, so no strip is left empty at the top. The position is read from the system at runtime: when folded, the lens moves to the outer screen's camera, and on other phones the layout adapts.

Reading entries in English needs an English voice installed on the phone. If there isn't one, the app tells you to install one in the system's text-to-speech settings.

## Project layout

```
app/src/main/
  assets/dex.json                     Offline Pokédex data in Chinese and English (built from PokeAPI CSVs)
  assets/model/pokemon_vit_int8.onnx  Recognition model (int8 quantized, 88 MB)
  java/app/pocketdex/
    data/      Pokédex data, artwork download and cache, settings, language
    ml/        On-device ONNX recognition (multi-scale centre crops), Claude check
    ui/        The two pages, buttons, D-pad, camera viewfinder, stat radar chart, UI text
    util/      Read-aloud and beeps
tools/         Scripts that regenerate dex.json and the ONNX model
```

## Open-source resources used

- Recognition model: [skshmjn/Pokemon-classifier-gen9-1025](https://huggingface.co/skshmjn/Pokemon-classifier-gen9-1025) (Apache-2.0, ViT-base fine-tuned on 1025 Pokémon), exported to ONNX and quantized to int8
- Pokédex data: [PokeAPI](https://github.com/PokeAPI/pokeapi) CSV data, plus Chinese Pokédex entries PokeAPI lacks from [42arch/pokemon-dataset-zh](https://github.com/42arch/pokemon-dataset-zh) (MIT)
- Artwork: downloaded at runtime from [PokeAPI/sprites](https://github.com/PokeAPI/sprites); not included in this repository

Pokémon and all Pokémon names, images and Pokédex text are © Nintendo / Creatures Inc. / GAME FREAK inc. / The Pokémon Company. This is an unofficial fan project for learning, not affiliated with or endorsed by them.

## License

PocketDex's own code is released under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). You may use, copy, modify and share it for any **noncommercial** purpose: personal projects, study, research, teaching, or use by charities, schools and public institutions. **Commercial use is not permitted.**

The recognition model, the Pokédex data sources and the Gradle wrapper keep their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

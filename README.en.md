[Español](README.md) | **English**

# RMXP-Android-Ports

Tools for porting RPG Maker XP games (mainly Pokémon Essentials fangames such
as Pokémon Uranium) to Android as a **standalone APK**, without JoiPlay or
other middleman apps.

## The idea

An RPG Maker XP game isn't code Android Studio can "understand": it's a set
of **Ruby scripts** (`Data/Scripts.rxdata`) plus maps, graphics and audio.
Translating all of that to Java/Kotlin by hand means remaking the game (which
is what [Project Uranium Godot](https://github.com/acedogblast/Project-Uranium-Godot) does).

The practical route:

1. Use **mkxp-z**, a free engine that runs RPG Maker XP/VX/VX Ace games
   without Windows. Pokémon Essentials v20+ already ships it as its official
   PC engine.
2. Build mkxp-z for Android (C++ with the NDK) and **bundle the game inside
   the APK**, with its own name, icon and touch controls.
3. Fix everything that depended on Windows (such as `Win32API`) with
   **`.rb` patches** that load before the game's scripts.

## Existing work (no need to start from scratch)

| Project | What it provides |
|---|---|
| [mkxp-z](https://github.com/mkxp-z/mkxp-z) | The engine (GPL-2.0). |
| [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android) | First native Android port of mkxp-z. Loads the game from `/storage/emulated/0/mkxp-z`. |
| [BookerRues9/mkxp-z-android-reworked](https://github.com/BookerRues9/mkxp-z-android-reworked) | Fixed-up version with a build guide (Ubuntu + NDK 23). |
| [vrcxz/mkxp-z-android-packager](https://github.com/vrcxz/mkxp-z-android-packager) | Already bundles a game into the APK with one script (`tools/build.sh game.zip`) and ships patches for Essentials v21. |

This repo builds on those projects and adds what they're missing.

## Tool 1: compatibility analyzer

`tools/rmxp_analyze.py` checks a game **before** you build and tells you what
will break on Android. It only reads; it changes nothing.

```
python tools/rmxp_analyze.py "C:\Games\Pokemon Uranium"
```

It checks:

- RGSS version (XP, VX or VX Ace) and Essentials version.
- Reads the scripts even when they're inside `Game.rgssad` (XP/VX encryption).
- Lists every `Win32API` call (Windows functions that don't exist on Android).
- Audio and video mkxp-z can't play (WMA, AVI, WMV…) and MIDI without a SoundFont.
- Extra DLLs, a missing `Fonts` folder, and game size.

Needs only Python 3.8 or newer. Pass `--json` for a JSON report. (Report text
is in Spanish.)

## Next steps

- [ ] Test the analyzer on real games (Uranium, Essentials v16–v21 games).
- [ ] `.rb` patches for the most common `Win32API` calls (keyboard, window, `.ini`).
- [ ] Per-game APK: its own name, icon and package id.
- [ ] On-screen touch controls and gamepad mapping.

## Legal note

The tool is free, but the games aren't: porting and distributing a fangame
needs its authors' permission, and Pokémon fangames use Nintendo's IP
(Uranium was taken down for that reason). Use it on games you have the right
to port.

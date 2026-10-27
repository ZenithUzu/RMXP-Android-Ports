[Español](README.md) | **English**

# RMXP-Android-Ports

Turns an RPG Maker XP, VX or VX Ace fangame (for example, one made with
Pokémon Essentials) into **its own Android app**, with its own name, icon
and signature. No JoiPlay or other middleman app needed.

On Windows, drag the game folder onto `Portar.bat`, check the details and
press **Crear APK** (Create APK). If your phone is connected, it installs
itself.

## What the app includes

| | |
|---|---|
| **Widescreen** | Shows more of the map on the sides on tall phone screens instead of black borders. "Original" and "Fill" are also available. |
| **Filters** | *Sharp smooth* (recommended: even pixels that fill the screen), *Pixel* (exact scaling), *Sharp*, *Smooth*, and old-screen scanlines. |
| **Smoothness and Hz** | *Auto* picks a refresh rate that's a multiple of 40 and 60 fps (such as 120 Hz), so every frame lasts the same and there's no stutter. *60 Hz* saves battery. The game always runs at its original speed. |
| **Fast forward up to x10** | On-screen fast-forward button; pick the speed (x2 to x10) in the menu. |
| **Options menu** | The ☰ button at the top right, the phone's Back button, or Select on a controller. The game pauses while it's open. |
| **Controls** | On-screen controls with adjustable opacity and size. USB and Bluetooth controllers work. |
| **Internet** | Native HTTP and HTTPS. Older fangames that go online through `wininet.dll` are redirected to Android's connection automatically. |
| **Safe updates** | The new version is prepared on the side and only replaces the old one once it's complete. Saves are never deleted or overwritten. |
| **Author signature** | Each game gets its own signature in its creator's name (read from the game's Readme). |

## What you need (once)

1. **Python 3.8 or newer**: [python.org](https://www.python.org/downloads/).
   When installing, check *Add python.exe to PATH*.
2. **Android Studio**: [developer.android.com/studio](https://developer.android.com/studio).
   Open it once so it downloads the SDK. It includes Java and `adb`.
3. To install straight to your phone: turn on **USB debugging**
   (Settings → About phone → tap "Build number" 7 times → Developer options →
   USB debugging) and plug in the cable.

You don't need to compile any C++. The first time, the tool downloads the
prebuilt engine from this repository's releases.

## How to port a game

1. Download this repository (**Code → Download ZIP**, or with GitHub Desktop).
2. Drag the game folder (the one with `Game.ini`) onto **`Portar.bat`**.
   You can also open `Portar.bat` and use **Elegir carpeta** (Choose folder).
3. Check what the tool found:
   - **Revisión** (check): warns you if the game uses Windows-only things that
     don't exist on Android (such as `Win32API` calls or `.wmv` videos).
   - **Name, Author and Version**: taken from `Game.ini` and the Readme. If the
     author isn't found, type it in.
   - **Icon** (PNG) and **SoundFont** (`.sf2`, only for games with MIDI music): optional.
4. Press **Crear APK**. The first time takes a few minutes while Gradle
   downloads what it needs; later builds are much faster.

The APK ends up in `C:\Users\<you>\RMXP-Android-Ports\apks\`.

## When the fangame releases a new version

Give the new folder to `Portar.bat` just like the first time. The tool
recognizes the game, uses **the same signature** and builds an APK that
installs **over** the previous one. On the phone:

- The new version is extracted into a separate folder. Only when it's done
  does it replace the old one.
- Everything the player created (saves, settings) is carried over to the new
  version without being overwritten.
- If the app closes halfway through, the old game stays untouched and it
  retries the next time the app opens.

> **Important:** keep a copy of `C:\Users\<you>\RMXP-Android-Ports\juegos\`.
> Each game's signature lives there. Without it, Android won't let a new
> version install over the old one, and you'd have to uninstall (which deletes
> saves). The tool never uninstalls anything on its own.

## About the signature

Android requires every app to be signed with a key, and every update to use
**the same** key. The tool creates a key for each game and puts the author's
and game's names in the certificate (for example
`CN=AuthorName, O=Pokémon Example`). The app id uses the author too:
`com.authorname.pokemonexample`.

It isn't the creator's private key: only they have that. If the creator
publishes their own official APK, theirs and yours can't be installed over
each other.

## If something goes wrong

- **"Probablemente NO funciona sin parches"** (probably won't work without
  patches): the game uses Windows functions. Many Essentials v20+ games work
  as they are; Essentials v16–v18 games usually need `.rb` patches (put them
  in `android/patches/`; they load before the game).
- **The game closes on the phone**: connect the phone and run
  `adb logcat -s mkxp-z SDL` in a terminal. That log says why.
- **No sound from MIDI music**: choose a `.sf2` SoundFont before building.

You can also check a game without building anything:

```
python tools\rmxp_analyze.py "C:\Games\My Fangame"
```

## For developers

```
Portar.bat                 Windows launcher (GUI)
tools/rmxp_port.py         the tool: checks, stages, signs, builds, installs
tools/rmxp_analyze.py      compatibility check (read-only)
android/                   Android Studio project (app + C++ engine)
android/patches/*.rb       Ruby patches loaded before the game
.github/workflows/motor.yml  builds the engine and publishes it as a release
```

- The app's new code is in `android/app/src/main/java/com/hatkid/mkxpz/OptionsMenu.java`,
  `MainActivity.java` (safe updates) and `android/app/src/main/res/` (menu and controls).
- Fast forward, pause and live filters are in
  `android/app/jni/mkxp-z/src/display/graphics.cpp` (the `__ANDROID__` block).
- **Build the engine yourself** (Linux or WSL): `cd android && bash tools/setup.sh && bash tools/build.sh --solo-motor`.
  The result is `android/build/motor-android.zip`; use it with `python tools/rmxp_port.py <game> --motor <lib folder>`.
- **Publish a new engine**: on GitHub, Actions → *Motor de Android* → *Run workflow*,
  using the same name as `MOTOR_VERSION` in `tools/rmxp_port.py`.

The tool's interface and messages are in Spanish; the Android menu follows
the phone's language (Spanish or English).

## Credits

This project builds on the work of:

- [**mkxp-z**](https://github.com/mkxp-z/mkxp-z): the engine that runs RPG Maker games (GPL-2.0-or-later). Based on Ancurio's [mkxp](https://github.com/Ancurio/mkxp).
- [**thehatkid/mkxp-z-android**](https://github.com/thehatkid/mkxp-z-android): the Android port of mkxp-z, with the on-screen controls.
- [**BookerRues9/mkxp-z-android-reworked**](https://github.com/BookerRues9/mkxp-z-android-reworked): fixes and build guide.
- [**vrcxz/mkxp-z-android-packager**](https://github.com/vrcxz/mkxp-z-android-packager): bundling the game into the APK, build scripts and Essentials patches. The `android/` folder starts from this project.
- [**SDL**](https://www.libsdl.org/), [**Ruby**](https://www.ruby-lang.org/), [OpenAL Soft](https://github.com/kcat/openal-soft), [PhysicsFS](https://github.com/icculus/physfs), [OpenSSL](https://www.openssl.org/), [Pixman](https://pixman.org/), [libogg, libvorbis and libtheora](https://xiph.org/), [uchardet](https://www.freedesktop.org/wiki/Software/uchardet/) and [libiconv](https://www.gnu.org/software/libiconv/): the libraries the engine uses.
- [**Pokémon Essentials**](https://github.com/Maruno17/pokemon-essentials): the patches target games made with it.

The three Android projects don't state a license of their own; since they're
derived from mkxp-z, they're distributed here under its GPL license.

## License

[GPL-2.0-or-later](LICENSE), the same as mkxp-z. You can use, change and
share this project, as long as you share the code of your changes too.

**Games are not included in or covered by this license.** Distributing a
fangame port needs its author's permission, and Pokémon fangames use
property of Nintendo, The Pokémon Company and Game Freak. Use the tool with
games you have the right to port.

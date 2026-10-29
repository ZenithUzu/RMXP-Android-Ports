> **RMXP-Android-Ports:** esta carpeta parte de [vrcxz/mkxp-z-android-packager](https://github.com/vrcxz/mkxp-z-android-packager). Para usar la herramienta, lee el [README principal](../README.md). / This folder is based on mkxp-z-android-packager; see the [main README](../README.en.md).

# mkxp-z Android Packager

This is a fork from [mkxp-z-android-reworked](https://github.com/BookerRues9/mkxp-z-android-reworked), for packaging game files into an apk.

# Using this project

- Open this repository on github codespace (or just locally if you are already familiar with gradle).

- Setup the dependencies:

```bash
./tools/setup.sh
```

- Build the apk:

```bash
./tools/build.sh /path/to/your/game.zip # it can be a folder, zip, rar, 7z, or tar*
```

- Download the output in:

```
app/build/outputs/apk/debug/mkxp-z-<version>-<abi>-debug.apk
```

---

### Note

This build targets `arm64-v8a` by default. To also support 32-bit ARM devices and
produce a universal APK, use:

```bash
./tools/build.sh /path/to/your/game.zip --all-abis
```

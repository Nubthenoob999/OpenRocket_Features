# Building OpenRocket with Gradle and jpackage

This project now includes a Gradle-driven packaging flow that reuses the existing runnable shadow JAR and feeds it into `jpackage`.

## Commands

Build the runnable JAR:

```powershell
.\gradlew.bat shadowJar
```

Create a platform-specific app image with `jpackage`:

```powershell
.\gradlew.bat jpackageAppImage
```

Run verification, build the JAR, and create the app image:

```powershell
.\gradlew.bat distJpackage
```

Create an installer for the current platform:

```powershell
.\gradlew.bat jpackageInstaller
```

On Windows, you can choose the installer format explicitly:

```powershell
.\gradlew.bat jpackageInstaller -PjpackageType=msi
.\gradlew.bat jpackageInstaller -PjpackageType=exe
```

If the runtime needs extra JDK modules that `jdeps` does not discover automatically, append them with:

```powershell
.\gradlew.bat jpackageAppImage -PjpackageExtraModules=jdk.unsupported,jdk.localedata
```

## Output locations

- Runnable JAR: `build/libs/OpenRocket-<version>.jar`
- jpackage input JAR copy: `build/jpackage/input/OpenRocket.jar`
- App image: `build/jpackage/app-image/`
- Installer artifacts: `build/jpackage/installer/`

## Notes

- `jpackageAppImage` is the safest packaging target because it does not require extra installer tooling.
- Windows `exe` and `msi` builds require the WiX Toolset on `PATH`.
- The Gradle build normalizes snapshot versions such as `26.xx-SNAPSHOT` into a numeric app version for `jpackage`.
- Packaged launchers follow the saved OpenRocket UI preference, so you can switch between Classic and Modern Preview inside the app.

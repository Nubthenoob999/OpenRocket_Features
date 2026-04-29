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

Build install4j installers for Windows, Linux, and macOS across x86_64/Intel plus ARM64/Apple Silicon targets:

```powershell
.\gradlew.bat install4jAllInstallers
```

Build only the Windows, Linux, or macOS install4j artifacts:

```powershell
.\gradlew.bat install4jWindowsInstallers
.\gradlew.bat install4jLinuxInstallers
.\gradlew.bat install4jMacInstallers
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
- install4j media: `build/install4j/media/`

## Notes

- `jpackageAppImage` is the safest packaging target because it does not require extra installer tooling.
- Windows `exe` and `msi` builds require the WiX Toolset on `PATH`.
- The Gradle build normalizes snapshot versions such as `26.xx-SNAPSHOT` into a numeric app version for `jpackage`.
- Packaged launchers follow the saved OpenRocket UI preference, so you can switch between Classic and Modern Preview inside the app.
- The install4j Gradle plugin will auto-provision install4j if `install4jHomeDir` or `INSTALL4J_HOME` is not set.
- The install4j tasks default to unsigned builds on this machine. Re-enable signing and notarization with `-Pinstall4jSign=true` after restoring the signing files under `install4j/26.xx/code_signing/`.
- Building the macOS installers from Windows requires an install4j Multi-Platform Edition. The auto-provisioned evaluation download can build the Windows media, but it skips the macOS media sets.

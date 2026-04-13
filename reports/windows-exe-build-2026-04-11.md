# Windows EXE build runbook (Gradle + shadowJar + jpackage)

Date: 2026-04-11
Workspace: OpenRocket_Features
OS: Windows 11
JDK: 17.0.12
Gradle: 9.2.0 (wrapper)

## Goal
Build a runnable JAR and package it into a Windows EXE installer.

## Verified output
- EXE: build/jpackage/installer/OpenRocket-26.0.0.exe
- Size: 143295488 bytes

## Important project-specific prerequisite
This codebase currently fails configuration if this file is missing:
- core/src/main/resources/datafiles/thrustcurves/thrustcurves.db

On this machine, only initial_motors.db existed. To unblock Gradle configuration, copy it once:

PowerShell:
Copy-Item core/src/main/resources/datafiles/thrustcurves/initial_motors.db core/src/main/resources/datafiles/thrustcurves/thrustcurves.db -Force

## Build steps that were executed
From repository root:

1) Build runnable fat JAR
PowerShell:
.\gradlew.bat shadowJar --console=plain --no-daemon

2) Build jpackage app image
PowerShell:
.\gradlew.bat jpackageAppImage --console=plain --no-daemon

3) Build EXE installer
PowerShell:
.\gradlew.bat jpackageInstaller -PjpackageType=exe --console=plain --no-daemon

## Why EXE initially failed
The task jpackageInstaller checks for WiX 3 candle.exe on PATH.
Without WiX 3, Gradle fails with:
"WiX Toolset was not found on PATH. Install WiX 3.x before running jpackageInstaller"

## No-admin WiX workaround used here
winget install of WiX 3 required admin rights, so a local portable WiX folder was used.

From repository root:

1) Download WiX 3 binaries zip
PowerShell:
New-Item -ItemType Directory -Path .\tools\wix3 -Force | Out-Null
curl.exe -sSL "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip" -o .\tools\wix3\wix314-binaries.zip

2) Extract
PowerShell:
Expand-Archive -Path .\tools\wix3\wix314-binaries.zip -DestinationPath .\tools\wix3 -Force

3) Run installer task with PATH prepended for this shell
PowerShell:
$env:PATH = "$PWD\tools\wix3;$env:PATH"
.\gradlew.bat jpackageInstaller -PjpackageType=exe --console=plain --no-daemon

## Artifacts produced by this flow
- Runnable jar: build/libs/OpenRocket-26.xx-SNAPSHOT.jar
- jpackage app image: build/jpackage/app-image/
- EXE installer: build/jpackage/installer/OpenRocket-26.0.0.exe

## Useful one-command verification
PowerShell:
Get-ChildItem .\build\jpackage\installer | Select-Object Name,Length,LastWriteTime

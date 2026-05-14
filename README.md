# Gerbarium Regions Runtime

Private Fabric 1.20.1 runtime mod for Gerbarium region activation, managed mob spawning, boundary control, runtime state persistence, admin commands, and admin GUI diagnostics.

## Build

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

Unix-like shell:

```bash
./gradlew clean build
```

## Runtime Config

Zone definitions are loaded from:

```text
config/gerbarium/zones/
```

Runtime state is stored under:

```text
config/gerbarium/zones-control/states/
```

Main admin command:

```text
/gerbzone
```

## License

All Rights Reserved. This repository is private/proprietary.

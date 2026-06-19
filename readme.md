# DeckMaker

This project is a tool for creating and managing custom card game templates and data.

![DeckMaker Preview](resources/img.png)

## How to Install

DeckMaker is distributed as native installers that include the required runtime, so end users do not need to install Java separately.

Download the latest installer from the [Releases](https://github.com/coretension/deckmaker/releases) page:

- **Windows:** Install `deckmaker-<version>.exe`.
- **Linux:** Install the `.deb` package.
- **macOS:** Install the `.pkg` package.

Windows installers use a stable upgrade ID, so future DeckMaker installers can upgrade the existing installation in place.

## Build From Source

You need Java 21 or later and Maven.

Run the app directly during development:

```sh
mvn javafx:run
```

Build the runnable JAR:

```sh
mvn clean package
```

Build a native installer for the current platform:

```sh
mvn clean package -Ddist=win
mvn clean package -Ddist=linux
mvn clean package -Ddist=mac
```

Installer output is written to:

- `target/dist-win/` for the Windows `.exe` installer.
- `target/dist-linux/` for the Linux `.deb` package.
- `target/dist-mac/` for the macOS `.pkg` package.

Native installers must be built on their target operating system because they use `jpackage`.

### Run the JAR Directly

If you have Java 21 or later installed, you can run the packaged JAR directly:

```sh
java -jar target/deckmaker-0.2.1-SNAPSHOT.jar
```

## Documentation

For a detailed guide on how to use DeckMaker, please refer to our:
- [User Guide](docs/User_Guide.md)

---
*Developed with the assistance of AI.*

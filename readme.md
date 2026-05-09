# CardMaker

This project is a tool for creating and managing custom card game templates and data.

![CardMaker Preview](resources/img.png)

## How to Download and Run
### Windows
To get started with CardMaker on Windows, follow these steps:

1. **Download the Zip File**: 
   - Go to the [Releases](https://github.com/coretension/cardmaker/releases) page (if applicable) or download the repository as a ZIP file.
   - Extract the contents of the ZIP file to a folder on your computer.

2. **Run the Application**:
   - Navigate to the extracted folder.
   - Look for the `cardmaker` directory.
   - Run the `cardmaker.exe` (or use the provided batch file if available).
   - Alternatively, if you have Maven installed, you can run `./mvnw javafx:run` from the root directory.

### macOS
To run on macOS:
1. **Prerequisites**: Ensure you have Java 21 or later installed.
2. **Build and Run**:
   - Clone the repository.
   - Run `./mvnw javafx:run` to run the application directly.
   - To build a native app image: `./mvnw package -Ddist=mac` (requires building on macOS).

### Run the JAR Directly (All OS, Java Required)
- If you have Java 21 or later installed, you can run the packaged JAR directly.
- From the project root, run:
  - `java -jar target/cardmaker-0.2.1-SNAPSHOT.jar`

## Documentation

For a detailed guide on how to use CardMaker, please refer to our:
- [User Guide](docs/User_Guide.md)

---
*Developed with the assistance of AI.*

# CaptureSDKDemo - HEVC Video Encoder

## Project Introduction

CaptureSDKDemo is an Android-based video capture and encoding demonstration project that implements the following key features:

- **HEVC/H.265 Video Encoding**: High-performance HEVC video encoding using MediaCodec
- **Frame Capture without UI Rendering**: Capture video streams through Surface mechanism without rendering to interface
- **TCP Network Transmission**: Support sending encoded video streams to remote servers via TCP protocol
- **Complete Lifecycle Management**: Proper handling of resource creation and release

## Environment Requirements

### Hardware Requirements
- Android device (supporting HEVC encoding)
- Network connection (for video streaming)

### Software Requirements
- **Java Version**: JDK 17
- **Android SDK**: API 35 or higher
- **Gradle Version**: 8.11.1 (recommended)
- **IDE**: Android Studio Hedgehog or higher

## Compilation Steps

### 1. Environment Configuration

#### Java 17 Configuration
Ensure JDK 17 is installed and environment variables are correctly configured:

```bash
# Check Java version
java -version

# Example output (should display Java 17)
java version "17.0.10"
```

#### Android SDK Configuration
Ensure Android SDK is installed and configure the correct path in `local.properties`:

```properties
# Windows example
sdk.dir=C:\Users\[YourUsername]\AppData\Local\Android\Sdk

# macOS example
sdk.dir=/Users/[YourUsername]/Library/Android/sdk
```

#### Gradle Configuration
Ensure using a compatible Gradle version (8.11.1 recommended):

```properties
# Configure Java 17 path in gradle.properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

### 2. Project Compilation

#### Using Android Studio
1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the project root directory and open
4. Wait for Gradle synchronization to complete
5. Click "Build" > "Build Bundle(s) / APK(s)" > "Build APK(s)"

#### Using Command Line

```bash
# Compile Debug version using gradlew script in project root
./gradlew assembleDebug

# Compile Release version
./gradlew assembleRelease

# Or use specific Gradle version
Some path to your\gradle-8.11.1\bin\gradle.bat assembleRelease
```

### 3. APK File Location

After successful compilation, APK files will be generated in the following locations:

```
app/build/outputs/apk/debug/app-debug.apk      # Debug version
app/build/outputs/apk/release/app-release.apk  # Release version
```

## Core Features

### 1. Frame Capture without UI Rendering

The project implements frame capture without UI rendering through the following mechanism:

```kotlin
// Create encoder input Surface
val inputSurface = mediaCodec?.createInputSurface()

// Pass Surface to capture SDK instead of rendering to SurfaceView
pxrCapture?.startPreview(inputSurface, renderMode, VIDEO_WIDTH, VIDEO_HEIGHT)
```

### 2. HEVC Video Encoding

HEVC encoding configuration:

```kotlin
val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
```

### 3. TCP Video Streaming

Encoded video streams are sent via TCP:

```kotlin
// Connect to server
fun connectToServer(serverIp: String, serverPort: Int) {
    socket = Socket(serverIp, serverPort)
    outputStream = socket?.getOutputStream()
}

// Send encoded data
outputStream?.write(dataSize)
outputStream?.write(encodedData)
outputStream?.flush()
```

## File Structure

```
CaptureSDKDemo/
├── app/
│   ├── build.gradle.kts           # App-level Gradle configuration
│   ├── libs/                      # Third-party libraries
│   │   └── capturelib-release.aar # Capture SDK library
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Application manifest
│           ├── java/com/picoxr/capturesdkdemo/
│           │   ├── MainActivity.kt   # Main activity
│           │   ├── VideoEncoder.kt   # Video encoder
│           │   └── permissions/      # Permission utilities
│           └── res/                  # Resource files
├── build.gradle.kts               # Project-level Gradle configuration
├── gradle.properties              # Gradle property configuration
├── local.properties               # Local environment configuration
└── README.md                      # Project documentation
```

## Contact Information

For questions or suggestions, please contact:
- GitHub: [https://github.com/FansenXi/MundanePicoCapture.git]


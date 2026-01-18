# AGENTS.md - CasterTV Development Guide

This document provides guidelines for AI agents working on the CasterTV Android project.

## Project Overview

CasterTV is an Android DLNA/UPnP casting service application written in Kotlin. It implements:
- Native SSDP/NSD discovery (no third-party UPnP libraries)
- ExoPlayer for media playback
- HTTP server for device description and control
- Multiple discovery protocols (DLNA, Google Cast, AirPlay)

## Build Commands

```bash
# Build
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew clean                  # Clean build artifacts

# Testing
./gradlew test                   # Run all unit tests
./gradlew test --tests "com.caster.tv.dlna.AVTransportServiceTest"  # Single test class
./gradlew test --tests "com.caster.tv.dlna.AVTransportServiceTest.play should change state"  # Single test

# Code Quality
./gradlew lint                   # Run lint checks
./gradlew build                  # Build + test + lint
```

## Code Style Guidelines

### Naming Conventions

| Element | Convention | Examples |
|---------|-----------|----------|
| Classes | PascalCase | `CastCoordinatorService`, `HttpRequest` |
| Functions | camelCase | `start()`, `initializePlayer()`, `handleRequest()` |
| Constants | UPPER_SNAKE_CASE | `ACTION_START`, `NOTIFICATION_ID` |
| Package-level variables | camelCase | `httpServer`, `exoPlayer` |
| LiveData backing props | `_` prefix | `_serviceStatus`, `serviceStatus` |
| Enums | PascalCase | `ServiceStatus.STOPPED` |
| Test methods | Backtick descriptive | `` `play should change state to PLAYING` `` |

### Imports Organization

Organize imports in this order with blank lines between groups:

```kotlin
import android.*                    # Android SDK
import androidx.*                   # AndroidX
import com.google.*                 # Google libraries
import org.jetbrains.*              # JetBrains/Kotlin
import timber.log.Timber            # Third-party (alphabetical)
import kotlinx.*                    # KotlinX
import java.*                       # Java standard library
import com.caster.tv.*              # Local project
```

### Formatting Rules

- **Indentation**: 4 spaces (Android Studio default)
- **Line length**: No hard limit, use IDE formatting
- **Braces**: K&R style (same line as declaration)
- **Blank lines**: Single blank line between logical sections
- **Semicolons**: Omitted (Kotlin standard)
- **Property access**: Use property syntax (`foo.bar` not `foo.getBar()`)

### Type System

```kotlin
// Prefer val over var
val serviceStatus: LiveData<ServiceStatus> = ...

// Use nullable types with safe calls
val mediaUri: String? = ...
mediaUri?.let { uri -> playMedia(uri) }

// Use type inference when obvious
val httpServer = SimpleHttpServer(...)

// Explicit types for public APIs and complex signatures
fun handleRequest(request: HttpRequest): HttpResponse

// Sealed classes for state
sealed class ServiceState {
    data object Stopped : ServiceState()
    data object Running : ServiceState()
    data class Error(val message: String) : ServiceState()
}
```

### Error Handling

```kotlin
// Always log exceptions with Timber
try {
    operation()
} catch (e: Exception) {
    Timber.e(e, "Failed to perform operation")
    // Handle or rethrow if needed
}

// Use safe calls for nullable operations
val player = exoPlayer ?: return

// Provide fallbacks for expected failure modes
private fun getLocalIpAddress(): String {
    return try {
        // ... network logic
        address.hostAddress ?: "192.168.0.49"  # Fallback
    } catch (e: Exception) {
        "192.168.0.49"  # Hard fallback
    }
}
```

### Logging

Use Timber with appropriate levels:

```kotlin
Timber.d("Debug information")      # Detailed debug
Timber.i("Operation completed")    # Important milestones
Timber.w("Something unexpected")   # Warnings
Timber.e(e, "Failed operation")    # Errors with exception
```

### Architecture Patterns

**Service Layer** (in `service/`):
- Foreground services with notification management
- Lifecycle awareness with LiveData
- Exposed via Binder for local binding

**DLNA Layer** (in `dlna/`):
- SSDP/NSD discovery protocols
- HTTP server for device description
- AVTransport/RenderingControl services

**Data Classes** (for DTOs):

```kotlin
data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)
```

### Kotlin Idioms

- **Scope functions**: Use appropriately (`apply`, `let`, `run`, `with`)
- **Lambda receivers**: `view.setOnClickListener { ... }`
- **String templates**: `"Device: $deviceName on port $port"`
- **Collection APIs**: `listOf()`, `mapOf()`, `forEach`, `joinToString`
- **Coroutines** for async operations (where applicable)

### Testing

```kotlin
@Test
fun `operation should produce expected result`() {
    // Given
    val service = AVTransportService()
    
    // When
    service.play()
    
    // Then
    assertEquals(TransportState.PLAYING, service.getTransportState())
}
```

- Use descriptive backtick test names
- Follow Given-When-Then structure
- Mock external dependencies with Mockito

### Project Structure

```
app/src/main/java/com/caster/tv/
├── CasterApplication.kt          # App entry point
├── service/
│   └── CastCoordinatorService.kt # Main coordinator
├── dlna/
│   └── upnp/                     # UPnP implementation
├── airplay/                      # AirPlay service
├── mirror/                       # Screen mirroring
├── control/                      # File casting
└── ui/                           # UI components

app/src/test/java/com/caster/tv/
└── dlna/
    └── AVTransportServiceTest.kt
```

### Key Dependencies

- **AndroidX**: Core-KTX, Lifecycle, Activity/Fragment, ConstraintLayout
- **Media3 ExoPlayer 1.2.1**: Media playback
- **Kotlinx Coroutines 1.7.3**: Async operations
- **OkHttp 4.12.0**: HTTP client
- **Timber 5.0.1**: Logging
- **Testing**: JUnit 4.13.2, Mockito 5.8.0

### Common Operations

```bash
# Check device via ADB
adb connect <TV_IP>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
adb logcat -d | grep -E "caster|SSDP|NSD"

# View logs
adb logcat -d | grep -E "CasterTV|CastCoordinator|AVTransport"
```

### Lint Configuration

In `app/build.gradle.kts`:
```kotlin
lint {
    warningsAsErrors = false
    abortOnError = false
    checkDependencies = true
}
```

Lint is informational only - failures don't block builds.

# Miracast Module - WiFi Display Receiver

**Updated:** 2026-06-15

WiFi Display (WFD) / Miracast receiver implementation for screen mirroring.

## OVERVIEW

Implements the WiFi Display sink role: discovers source devices via WiFi P2P/NSD, receives H.264 video via RTP, decodes with MediaCodec, renders to Presentation display.

## STRUCTURE

```
miracast/
├── MiracastManager.kt           # Main singleton coordinator
├── discovery/
│   ├── MiracastDevice.kt        # Device data class
│   └── MiracastDiscoveryManager.kt  # WiFi P2P channel, NSD registration
├── streaming/
│   ├── WfdRTSPServer.kt         # RTSP server (port 7236)
│   ├── WfdRTSPHandler.kt        # WFD protocol handler
│   ├── RTPVideoReceiver.kt      # RTP packet receiver
│   └── VideoStreamProcessor.kt  # NAL unit reassembly
├── renderer/
│   ├── H264Decoder.kt           # MediaCodec H.264 decoder
│   ├── VideoRendererPipeline.kt # Decode → render pipeline
│   └── SurfaceManager.kt        # Surface lifecycle
├── presentation/
│   ├── MiracastPresentation.kt  # Presentation dialog for display
│   └── MiracastDisplayManager.kt # Display selection/management
└── control/
    └── FrameSyncController.kt   # Frame timing control
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Start/stop receiver | `MiracastManager.kt` | Singleton via `initialize()` |
| Add RTSP command | `WfdRTSPHandler.kt` | Parse/respond to RTSP methods |
| Modify video pipeline | `VideoRendererPipeline.kt` | MediaCodec configuration |
| Change display behavior | `MiracastPresentation.kt` | SurfaceView setup |
| Debug connection issues | `MiracastDiscoveryManager.kt` | WiFi P2P, NSD registration |

## PROTOCOL FLOW

```
1. NSD registers "_wifi-display._tcp." service (port 7236)
2. Source connects → RTSP OPTIONS/DESCRIBE/SETUP/PLAY
3. RTP stream starts on negotiated port (50000+)
4. H264Decoder receives NAL units → MediaCodec → Surface
5. TEARDOWN stops stream, releases resources
```

## CONVENTIONS

### Initialization Order (CRITICAL)

```kotlin
// MUST be called in this order:
MiracastDiscoveryManager.initialize(context)
MiracastManager.initialize(context)
```

### Singleton Pattern

```kotlin
companion object {
    @Volatile private var instance: MiracastManager? = null
    
    fun getInstance(): MiracastManager = instance 
        ?: throw IllegalStateException("Not initialized")
}
```

### Coroutine Scope

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
```

## ANTI-PATTERNS

| Pattern | Location | Fix |
|---------|----------|-----|
| `reassemblyBuffer!!` | `VideoStreamProcessor.kt:170,174` | Use `?.let` or `?: return` |
| `activeDisplay!!` | `MiracastDisplayManager.kt:133` | Null-safe access |
| `surfaceView!!` | `MiracastPresentation.kt:107` | Check null before use |
| `decoderThread!!` | `VideoRendererPipeline.kt:69` | Guard with null check |

## KEY PORTS

- **RTSP**: 7236 (WiFi Display standard)
- **RTP**: 50000+ (negotiated via RTSP SETUP)

## TESTING

```bash
./gradlew test --tests "com.kbtv.caster.miracast.*"
```

Test file: `app/src/test/java/com/kbtv/caster/miracast/MiracastUnitTest.kt`
- Contains 3 test classes: H264DecoderTest, MiracastDeviceTest, FrameSyncControllerTest

## NOTES

- Uses Android MediaCodec (not external library) for H.264 decoding
- NSD + WiFi P2P both registered for broader device compatibility
- Auto-starts receiver mode on initialization

# AGENTS.md - KBTVCaster

**Updated:** 2026-06-15 | **Commit:** 962b207 | **Branch:** master

Android TV casting receiver: DLNA (Cling UPnP), Miracast (WiFi Display), AirPlay (stub). ExoPlayer playback, foreground service.

## BEHAVIORAL RULES

**Think before coding.** State assumptions. If confused, stop and ask. Surface tradeoffs.
**Simplicity first.** Minimum code that solves the problem. No speculative abstractions.
**Surgical changes.** Touch only what you must. Match existing style. Every changed line traces to the request.
**Goal-driven.** Define success criteria as verifiable outcomes. Loop until proven.

## QUICK REFERENCE

```bash
./gradlew assembleDebug                                    # Debug APK
./gradlew assembleRelease                                  # Release APK (no signing config)
./gradlew test                                             # All unit tests
./gradlew test --tests "com.kbtv.caster.dlna.*"           # DLNA tests only
./gradlew test --tests "com.kbtv.caster.miracast.*"       # Miracast tests only
./gradlew lint                                             # Lint (non-blocking, informational)
adb logcat -s "DLNAUtils:*" "CastCoordinator:*" "Timber:*" # Debug log filter
```

**Build:** AGP 8.5.0, Kotlin 1.9.24, Gradle 8.7, JDK 11, compileSdk 34, minSdk 23.

## ARCHITECTURE

```
Phone (DLNA/Miracast source)
    │
    ▼
CastCoordinatorService (foreground, START_STICKY)
  ├── DLNA: DLNAUtils → AndroidUpnpServiceImpl (Cling) → ZxtMediaRenderer
  │     └── AVTransport + RenderingControl UPnP services
  │     └── ZxtMediaPlayer creates its OWN ExoPlayer (separate from service's)
  ├── Miracast: MiracastDiscoveryManager (WiFi P2P + JmDNS) → WfdRTSPServer (Netty, :7236)
  │     └── RTPVideoReceiver (:50000+) → VideoStreamProcessor → H264Decoder → Surface
  ├── ExoPlayer #1 (service-owned, for direct UI playback)
  └── AirPlay: NOT WIRED IN (AirPlayManager.start() is a TODO stub)

UI: MainActivity binds service via LocalBinder → gets ExoPlayer + Miracast StateFlow
Boot: BootReceiver → CastCoordinatorService(ACTION_START) after 5s sleep
```

**Design decisions (non-obvious):**
- `kotlin.incremental=false` — Cling source compilation requires it
- `usesCleartextTraffic=true` — DLNA/UPnP needs unencrypted HTTP
- Device name hardcoded as "凯机投屏" in CastCoordinatorService.onCreate
- No DI framework — singletons via companion object DCL pattern
- No ViewModel — Activity binds directly to service LiveData/StateFlow

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Start/stop all protocols | `service/CastCoordinatorService.kt` | `start()` / `stop()`, coordinates DLNA+Miracast |
| Add DLNA command | `dlna/DLNAUtils.java` → `com/zxt/dlna/dmr/` | Java bridge to Cling. `AVTransportService.java` for UPnP actions |
| Change DLNA device name | `service/CastCoordinatorService.kt` `onCreate` | Hardcoded, not from resources |
| Miracast RTSP protocol | `miracast/streaming/WfdRTSPHandler.kt` | Parse/respond to RTSP methods |
| Miracast video decode | `miracast/renderer/H264Decoder.kt` | MediaCodec H.264. **Package mismatch:** file in `renderer/` but declares `package streaming` |
| RTP packet handling | `miracast/streaming/VideoStreamProcessor.kt` | FU-A/STAP-A NAL reassembly |
| UI / remote keys | `ui/MainActivity.kt` | D-pad media keys, PlayerView lifecycle |
| Boot auto-start | `receiver/BootReceiver.kt` | BOOT_COMPLETED + QUICKBOOT_POWERON + REBOOT |
| AirPlay (incomplete) | `airplay/AirPlayManager.kt`, `airplay/server/AirPlayRTSPServer.kt` | Stubs with TODOs. Not initialized by service |
| Notification channels | `CasterApplication.kt` | Creates `caster_service`, `caster_playback` |
| UPnP service registration | `AndroidManifest.xml` → `AndroidUpnpServiceImpl` | Cling service, not exported |
| Versioning | `.opencode/skills/kbtv-versioning/` | Changelog template + scripts. Releases in `releases/` |

## STRUCTURE

```
app/src/main/java/
├── com/kbtv/caster/              # App code (22 Kotlin + 1 Java)
│   ├── CasterApplication.kt      # Entry: Timber, notification channels, SAX driver
│   ├── service/CastCoordinatorService.kt  # Central foreground service
│   ├── receiver/BootReceiver.kt  # Auto-start (BOOT/QUICKBOOT/REBOOT)
│   ├── ui/                       # MainActivity, MiracastDeviceAdapter
│   ├── dlna/DLNAUtils.java       # Java bridge: binds Cling, creates ZxtMediaRenderer
│   ├── airplay/                  # AirPlayManager + RTSPServer (STUB, not wired in)
│   └── miracast/                 # WiFi Display sink (see miracast/AGENTS.md)
├── org/fourthline/cling/         # EMBEDDED: Cling UPnP (533 Java files) — DO NOT MODIFY
└── com/zxt/dlna/                 # EMBEDDED: ZxtMediaRenderer DMR (17 Java files)
app/libs/                         # 20 JARs: Jetty 8.1, Cling support, seamless, HTTP, servlet
app/src/test/                     # 3 unit test files (0 instrumented tests)
```

## CONVENTIONS

**Only what differs from Kotlin/Android defaults:**

```kotlin
// Logging: Timber in Kotlin, android.util.Log in Java (DLNAUtils, com.zxt.dlna)
Timber.d("state: $value")                    // String interpolation, never concatenation
Timber.e(e, "Failed to initialize")          // Throwable as 2nd arg

// Error handling: catch+log, never re-throw
try { risky() } catch (e: Exception) { Timber.e(e, "desc"); /* fallback or return */ }

// Safe calls preferred — force unwrap !! is an anti-pattern (see GOTCHAS)
val player = exoPlayer ?: return

// State exposure: _ backing field + public readonly
private val _status = MutableLiveData(Status.STOPPED)
val status: LiveData<Status> = _status
// StateFlow variant:
val state: StateFlow<S> = _state.asStateFlow()

// Sealed classes for state machines
sealed class ConnectionState {
    data object IDLE : ConnectionState()
    data class Error(val msg: String) : ConnectionState()
}

// Singleton via companion object DCL
companion object {
    @Volatile private var instance: Foo? = null
    fun initialize(ctx: Context) = instance ?: synchronized(this) {
        instance ?: Foo(ctx.applicationContext).also { instance = it }
    }
}
```

| Element | Pattern | Example |
|---------|---------|---------|
| Constants | `UPPER_SNAKE_CASE` in companion object | `ACTION_START`, `RTSP_PORT` |
| LiveData/StateFlow backing | `_` prefix | `_serviceStatus` |
| Test methods | Backticks | `` `decoder should not be configured initially` `` |
| TAG constant | Vestigial `private const val TAG = "..."` | Unused — Timber auto-tags. Safe to ignore. |

**Import order:** `android.*` → `androidx.*` → `com.google.*` → `timber.log.Timber` → `kotlinx.*` → `java.*` → `com.kbtv.caster.*` → `com.zxt.dlna.*`

**Mixed-language logging:** Chinese messages common in miracast/ module (e.g. `"视频接收错误"`). Match surrounding file's language.

## GOTCHAS

| Issue | Location | Impact | Fix |
|-------|----------|--------|-----|
| **Dual ExoPlayer** | CastCoordinatorService + ZxtMediaPlayer | DLNA playback may trigger 2 ExoPlayer instances simultaneously | Route all playback through one owner |
| **Notification channel mismatch** | CasterApplication creates `caster_service` / `caster_playback`; CastCoordinatorService uses `castertv_channel` | Foreground service crash on API 26+ | Align channel IDs |
| **BootReceiver blocks** | `BootReceiver.kt:41` — `Thread.sleep(5000)` in `onReceive` | Blocks broadcast dispatcher thread | Use `Handler.postDelayed` or WorkManager |
| **H264Decoder package mismatch** | File at `miracast/renderer/` declares `package streaming` | Confusing, potential build issues | Fix package declaration |
| **Test package mismatch** | `TransportStateTest.kt`, `MediaUriTest.kt` declare `com.caster.tv.dlna` | Tests may not match refactored package | Update to `com.kbtv.caster.*` |
| Force unwrap `!!` | `VideoStreamProcessor.kt:170,174`, `VideoRendererPipeline.kt:69`, `MiracastDisplayManager.kt:133`, `MiracastPresentation.kt:107` | NPE risk in RTP hot path | Use `?.let` or `?: return` |
| Dead Application classes | `airplay/AirPlayApplication.kt`, `miracast/MiracastApplication.kt` | Not in manifest, never loaded | Delete or document as unused |
| **versionName stale** | `build.gradle.kts:15` says `0.1.1` but latest release is v0.2.1 | Version mismatch | Bump versionName |
| No instrumented tests | `androidTest/` missing despite espresso deps | UI untested | Remove unused deps or add tests |
| Cling is source, not Maven | `org/fourthline/cling/` (533 files) | Must not refactor — treat as vendor code | Modify only `com.kbtv.caster.*` and `com.zxt.dlna.*` |

## TESTING

```kotlin
@RunWith(MockitoJUnitRunner::class)
class FooTest {
    @Mock lateinit var mock: Bar

    @Before fun setup() { /* ... */ }

    @Test
    fun `decoder should not be configured initially`() {
        `when`(mock.state).thenReturn(IDLE)
        assertEquals(IDLE, systemUnderTest.state)
    }
}
```

- **Framework:** JUnit 4 + Mockito 5 + mockito-kotlin
- **Naming:** Backtick descriptive names
- **Multiple test classes per file** allowed (see `MiracastUnitTest.kt`: 3 classes)
- **No Robolectric, no Espresso** — pure JVM unit tests only
- **Test files:** `dlna/TransportStateTest.kt`, `control/MediaUriTest.kt`, `miracast/MiracastUnitTest.kt`

## NOTES

- **Embedded libraries** (`org.fourthline.cling.*`, `com.zxt.dlna.*`): Source files, not Maven deps. Treat as read-only vendor code. 550 Java files total.
- **`app/libs/`**: 20 JARs (Jetty 8.1.9, Cling support 2.0.1, seamless-http/xml/util, Apache HTTP). Excluded from slf4j to avoid conflicts.
- **Netty + JmDNS**: Maven deps for Miracast RTSP server and mDNS discovery. Not in libs/.
- **Sub-module docs**: See `miracast/AGENTS.md` for Miracast protocol details.
- **Release process**: Use `.opencode/skills/kbtv-versioning/` skill. Changelogs in `releases/`.
- **Lint**: Non-blocking (`abortOnError=false`). Informational only.
- **`temp/` directory**: Working notes and scripts. Not in `.gitignore` but should be.

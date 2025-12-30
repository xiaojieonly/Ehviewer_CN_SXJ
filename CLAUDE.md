# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EhViewer is an Android application for browsing E-Hentai, a fork maintained by xiaojieonly with Chinese localization and enhancements. This is a mature codebase with 525+ source files (Java + Kotlin mix), native C++ components, and complex image processing capabilities.

**Key Information:**
- Main branch: `BiLi_PC_Gamer`
- Current version: 2.0.0.9 (versionCode 111)
- Application ID: `com.xjs.ehviewer`
- Min SDK: 23 (Android 6.0) | Target SDK: 29 | Compile SDK: 35
- Java Version: 21

## Build Commands

### Basic Build
```bash
# Windows
gradlew app:assembleDebug          # Build debug APK
gradlew app:assembleAppRelease     # Build release APK
gradlew clean                       # Clean build artifacts

# Linux/Mac
./gradlew app:assembleDebug
./gradlew app:assembleAppRelease
./gradlew clean
```

**Output location:** `app/build/outputs/apk/`

### Testing
```bash
gradlew test                        # Run all unit tests
gradlew testDebugUnitTest          # Run debug unit tests
gradlew testAppReleaseUnitTest     # Run release unit tests
```

**Test location:** `app/src/test/`

### Linting
```bash
gradlew lint                        # Run lint checks
gradlew lintDebug                  # Lint debug variant
gradlew lintAppRelease             # Lint release variant
```

### License Report
```bash
gradlew licenseAppReleaseReport    # Generate license report for dependencies
```

### Native Build
The project includes native C++ code built via CMake:
```bash
gradlew externalNativeBuildDebug   # Build native libraries
```

**Native code location:** `app/src/main/cpp/`
**CMake config:** `app/src/main/cpp/CMakeLists.txt`

## Code Architecture

### High-Level Structure

```
EhApplication (singleton entry point)
├── EhDB (GreenDAO ORM - database access)
├── DownloadManager (download orchestration)
├── SpiderQueen (image downloading crawler)
├── EhClient (HTTP networking via OkHttp3 3.14.7)
├── EhEngine (main API/business logic layer)
├── Settings (SharedPreferences configuration)
└── EventBus (GreenRobot EventBus for event distribution)
```

### Architectural Pattern: MVP (Model-View-Presenter)
- **View:** Activities and Scenes (custom fragment-based navigation)
- **Presenter:** Managers (DownloadManager, SpiderQueen) and EhEngine
- **Model:** GreenDAO entities and data classes
- **Communication:** EventBus for cross-component messaging

### Key Package Structure

**`com.hippo.ehviewer` (core application):**
- `EhApplication.java` - Application entry point and global state
- `EhDB.java` - GreenDAO database manager (SQLite ORM)
- `Settings.java` - Centralized SharedPreferences configuration
- `Analytics.kt` - Firebase crash reporting (optional)

**`com.hippo.ehviewer.ui` (UI layer):**
- **Activities:** `MainActivity`, `GalleryActivity`, `SettingsActivity`, `SplashActivity`
- **Scenes:** Custom fragment-based navigation (~46 scene files)
  - Base: `BaseScene` extends `SceneFragment`
  - Key scenes: `GalleryListScene`, `GalleryDetailScene`, `DownloadScene`, `HistoryScene`
  - Note: Uses custom `scene` framework, not Jetpack Navigation

**`com.hippo.ehviewer.client` (networking):**
- `EhClient.java` - OkHttp3 HTTP client wrapper
- `EhEngine.java` - Main API layer for E-Hentai operations
- `EhConfig.java` - E-Hentai configuration constants
- `EhUrl.java` - URL building utilities
- `EhHosts.java` - Host resolution with built-in Chinese CDN support
- `EhTagDatabase.java` - Tag database management
- `client/data/` - Data models (GalleryInfo, GalleryDetail, etc.)
- `client/parser/` - 22 parser classes for HTML/API responses (uses JSoup)

**`com.hippo.ehviewer.dao` (data persistence):**
- GreenDAO-generated DAOs for all entities
- Key DAOs: `DownloadsDao`, `HistoryDao`, `QuickSearchDao`, `FilterDao`, `BlackListDao`
- Local storage: `LocalFavoritesDao`, `BookmarksBao`, `GalleryTagsDao`

**`com.hippo.ehviewer.download` (download management):**
- `DownloadManager.java` - Centralized download orchestration (51KB, complex)
- `DownloadService.kt` - Foreground service for downloads
- `DownloadTorrentManager.kt` - Torrent download support
- `DownloadInfo.java` - Download metadata entity

**`com.hippo.ehviewer.spider` (image crawling):**
- `SpiderQueen.java` - Main spider/crawler for image fetching (66KB)
- `SpiderDen.java` - Spider persistence and cache management
- `SpiderInfo.java` - Spider metadata

**`com.hippo.network` (network utilities):**
- SSL/TLS security: `EhSSLSocketFactory`, `Tls12SocketFactory`, `EhX509TrustManager`
- Cookie management: `CookieRepository`, `CookieDatabase`

**Supporting libraries (all under `com.hippo`):**
- `glgallery` - OpenGL-based gallery view rendering
- `glview` - OpenGL view components
- `image` - Image processing with JNI bindings
- `conaco` - Image caching library (LRU cache)
- `beerbelly` - Disk cache implementation
- `scene` - Custom scene/fragment navigation framework
- `widget` - Custom Android widgets
- `unifile` - Unified file access (supports content URIs)

### Native Code (JNI/NDK)

**Native library:** `libehviewer.so` (module name: `native-lib`)

**Components:**
1. **GIF handling:** `gif/native-lib.cpp`, `gif/dgif_lib.c`, `gif/gifalloc.c`
2. **Image processing:** JPEG (libjpeg-turbo), PNG (libpng), WebP, GIF
3. **Filters:** CLAHE, grayscale, pixel operations, scaling

**Supported ABIs:** armeabi-v7a, arm64-v8a, x86, x86_64

## Development Guidelines

### Working with Parsers

All E-Hentai API responses go through parser classes in `client/parser/`. When modifying parsing logic:
1. Check corresponding test in `app/src/test/` (e.g., `GalleryListParserTest`)
2. Use JSoup for HTML parsing
3. Handle null values defensively (E-Hentai responses can be inconsistent)

### Database Changes

Database uses GreenDAO 3.0.0. To modify database schema:
1. Update entity classes in `com.hippo.ehviewer.dao` package
2. Regenerate DAOs using the `daogenerator` module
3. Handle migrations in `EhDB.java`

### Scene Navigation

This app uses a custom Scene framework instead of Jetpack Navigation:
- New screens extend `BaseScene` (not Fragment directly)
- Scene transitions handled via `SceneFragment` methods
- Find scene examples in `ui/scene/` package

### Event Communication

Uses GreenRobot EventBus 3.3.1 for component communication:
- Subscribe: `@Subscribe(threadMode = ThreadMode.MAIN)`
- Post events: `EventBus.getDefault().post(event)`
- Register/unregister in lifecycle methods

### Settings and Configuration

All app settings managed through `Settings.java`:
- Static methods for reading/writing preferences
- Centralized configuration - do NOT access SharedPreferences directly
- E-Hentai specific config in `EhConfig.java`

### Host Configuration

The app includes built-in host resolution for Chinese users:
- Edit `EhHosts.java` for host changes
- Supports custom hosts and DNS-over-HTTPS
- See FAQ document: `feedauthor/EhviewerIssue.md` for common network issues

### Image Loading and Caching

Two-tier image system:
1. **Conaco library** - Custom LRU memory cache
2. **Spider system** - Downloads and caches images locally
3. **Native processing** - Performance-critical operations in C++

Do NOT use Glide directly - it's integrated through Conaco.

### Firebase Analytics (Optional)

Firebase Crashlytics and Analytics are conditionally enabled:
- Only applied if `google-services.json` exists
- Managed through `Analytics.kt`
- Can be disabled by removing `google-services.json`

## Common Tasks

### Adding a New Gallery Scene

1. Create scene class extending `BaseScene` in `ui/scene/`
2. Implement required lifecycle methods
3. Add scene transition in parent scene
4. Update navigation logic if needed

### Adding New E-Hentai API Endpoint

1. Add URL building in `EhUrl.java`
2. Create data model in `client/data/`
3. Implement parser in `client/parser/`
4. Add parser test in `app/src/test/`
5. Add API method in `EhEngine.java`

### Modifying Download Behavior

Key files to modify:
- `DownloadManager.java` - Core download logic
- `DownloadService.kt` - Foreground service
- `DownloadScene.java` - UI for downloads
- `SpiderQueen.java` - Image fetching logic

### Adding Language Support

1. Add language code to `resourceConfigurations` in `app/build.gradle`
2. Create `res/values-{lang}/strings.xml`
3. Translate all strings (check existing translations as reference)

## Important Notes

### Network Access
- App designed for Chinese users with built-in CDN hosts
- Supports both direct connection (裸连) and VPN usage
- See common network configuration: `feedauthor/EhviewerIssue.md`

### Security
- Uses Conscrypt 2.5.3 for modern TLS
- Custom SSL socket factories for compatibility
- Cookie management with `EhCookieStore`

### Testing Strategy
Current tests focus on:
- Parser validation (most critical)
- Host resolution logic
- Cookie management
- Natural sorting algorithms

When adding features, prioritize parser tests.

### Known Constraints
- OkHttp3 version locked at 3.14.7 (stable, not latest)
- Target SDK 29 (intentionally not updated to latest)
- GreenDAO 3.0.0 (legacy ORM, not Room)
- Custom Scene framework (not Jetpack Navigation)

### Dependency Conflicts
The `build.gradle` forces specific versions for stability:
```groovy
force 'com.github.seven332:glgallery:25893283ca'
force 'com.github.seven332:glview:ba6aee61d7'
force 'com.github.seven332:image:09b43c0c68'
```
Do NOT update these without thorough testing.

## Troubleshooting

### Build Issues
- Ensure JAVA_HOME points to JDK 21
- Check Gradle version: 8.13 (via wrapper)
- Clean and rebuild: `gradlew clean app:assembleDebug`

### Native Build Failures
- Check NDK installation
- Verify CMake version compatibility
- Review `app/src/main/cpp/CMakeLists.txt`

### Test Failures
- Most tests use Robolectric for Android mocking
- Ensure test resources are included: `testOptions.unitTests.includeAndroidResources = true`
- Run specific test: `gradlew test --tests <TestClassName>`

### Common Issues Reference
See `feedauthor/EhviewerIssue.md` for:
- App crashes and freezes
- 509 errors
- Download failures
- Login/authentication issues

## Project Resources

- **FAQ:** `feedauthor/EhviewerIssue.md`
- **Changelog:** See README.md or `feedauthor/year*.md`
- **License:** See LICENSE file
- **Dependencies:** Run `gradlew licenseAppReleaseReport`

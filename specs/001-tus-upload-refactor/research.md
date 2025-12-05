# Research: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-05

> ⚠️ **CRITICAL CONSTRAINT**: Use existing TUS libraries only. Do NOT implement TUS protocol from scratch.

## Research Tasks

### 1. TUS Server Library Selection

**Task**: Find a production-ready TUS server library compatible with Android

**Decision**: Use **tus-java-server** (v1.0.0-2.x) + **Jetty Embedded** (v11.x)

**Rationale**: 
- `tus-java-server` is the official Java implementation, MIT licensed, 160+ GitHub stars
- Implements TUS v1.0.0 with ALL optional extensions (creation, checksum, expiration, termination, concatenation)
- Well-tested with Uppy and tus-js-client
- Only dependency: Jakarta Servlet API (provided by Jetty)
- Version 1.0.0-2.x uses Java 11+ (compatible with Android), avoids Jakarta namespace issues

**Alternatives Considered**:
| Option | Status | Reason |
|--------|--------|--------|
| Implement TUS from scratch | ❌ REJECTED | High risk, protocol complexity, 5-7 days effort |
| NanoHTTPD + custom TUS | ❌ REJECTED | Still implementing protocol, error-prone |
| NanoHTTPD + Servlet adapter | ❌ REJECTED | Hacky, incomplete API coverage |
| tusd (Go server) | ❌ REJECTED | Not embeddable in Android |

**Why Jetty (not other Servlet containers)**:
- Jetty 11.x is lightweight (~2-3 MB), embeddable, actively maintained
- Works on Android (used by other Android projects)
- Simple programmatic server setup (no XML config)
- Version 11 uses `javax.servlet` (compatible with tus-java-server 1.0.0-2.x)

---

### 2. tus-java-server Integration Pattern

**Task**: Research how to integrate tus-java-server with custom storage (MediaStore)

**Decision**: Implement custom `UploadStorageService` interface

**Rationale**:
- tus-java-server provides `UploadStorageService` interface for custom storage backends
- We implement `MediaStoreUploadStorageService` that writes to Android MediaStore
- Library handles ALL protocol logic; we only handle where bytes go

**Key Interface Methods to Implement**:
```kotlin
interface UploadStorageService {
    fun getUploadInfo(uploadUrl: String, ownerKey: String?): UploadInfo?
    fun create(info: UploadInfo, ownerKey: String?): UploadInfo
    fun append(info: UploadInfo, inputStream: InputStream): UploadInfo
    fun getUploadedBytes(uploadUrl: String, ownerKey: String?): InputStream?
    fun terminateUpload(info: UploadInfo, ownerKey: String?)
    fun cleanupExpiredUploads(lockingService: UploadLockingService?)
}
```

**Storage Strategy**:
- Upload metadata → Room Database (UploadSession entity)
- File bytes → MediaStore via existing `MediaStoreUploader`
- Use `IS_PENDING=1` flag to hide incomplete uploads

---

### 3. tus-js-client Integration

**Task**: Research best practices for integrating tus-js-client in embedded web assets

**Decision**: Use tus-js-client v4.x minified build, bundled in assets/transfer/

**Rationale**:
- Official client library with excellent browser support
- Handles chunking, retries, and fingerprinting automatically
- Same library tested with tus-java-server (compatibility guaranteed)

**Integration Approach**:
1. Download `tus.min.js` from npm package (v4.1.0)
2. Include via `<script>` tag in index.html
3. Configure with server endpoint `/tus/` 

**Key Configuration**:
```javascript
const upload = new tus.Upload(file, {
    endpoint: "/tus/",
    retryDelays: [0, 1000, 3000, 5000],
    chunkSize: 5 * 1024 * 1024, // 5MB chunks
    metadata: { filename: file.name, filetype: file.type },
    onProgress: (bytesUploaded, bytesTotal) => { ... },
    onSuccess: () => { ... },
    onError: (error) => { ... }
});
upload.start();
```

---

### 4. Jetty Embedded Setup for Android

**Task**: Research Jetty Embedded configuration for Android environment

**Decision**: Use Jetty 11.0.x with programmatic configuration

**Key Setup**:
```kotlin
val server = Server(port)
val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
context.contextPath = "/"
context.addServlet(ServletHolder(TusUploadServlet(tusService)), "/tus/*")
context.addServlet(ServletHolder(StaticAssetsServlet()), "/*")
server.handler = context
server.start()
```

**Dependencies** (add to build.gradle.kts):
```kotlin
implementation("org.eclipse.jetty:jetty-server:11.0.18")
implementation("org.eclipse.jetty:jetty-servlet:11.0.18")
implementation("me.desair.tus:tus-java-server:1.0.0-2.5")
```

---

### 5. Migration from NanoHTTPD

**Task**: Plan migration from NanoHTTPD to Jetty

**Decision**: Full replacement (not adapter pattern)

**What Changes**:
| Component | Before | After |
|-----------|--------|-------|
| HTTP Server | `NanoHTTPD` class | `Jetty Server` |
| Request handling | `IHTTPSession` | `HttpServletRequest` |
| Response building | `newFixedLengthResponse()` | `HttpServletResponse` |
| File uploads | Custom `TempFileManager` | tus-java-server handles |
| Static assets | Custom `serveAsset()` | `DefaultServlet` or custom |

**What Stays Same**:
- `MediaStoreUploader` (low-level MediaStore operations)
- Callback interfaces (`onFileUploaded`, `onUploadProgress`, etc.)
- TransferService lifecycle management

---

## Resolved Clarifications

| Unknown | Resolution |
|---------|------------|
| TUS server implementation | tus-java-server library (NO custom implementation) |
| HTTP server | Jetty Embedded 11.x (replaces NanoHTTPD) |
| TUS client | tus-js-client v4.x |
| Custom storage | Implement `UploadStorageService` interface |
| Protocol extensions | All included in tus-java-server (creation, checksum, expiration, termination) |


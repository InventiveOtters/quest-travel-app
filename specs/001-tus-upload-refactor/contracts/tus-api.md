# TUS API Contract

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-05  
**Protocol Version**: TUS 1.0.0  
**Implementation**: tus-java-server library (NO custom implementation)

## Base URL

```
http://{device-ip}:{port}/tus/
```

Port: 8080 (with fallback to 8081, 8082, 8083)

## Library-Provided Endpoints

The **tus-java-server** library handles all TUS protocol endpoints automatically:

| Endpoint | Method | Purpose | Handled By |
|----------|--------|---------|------------|
| `/tus/` | OPTIONS | Capability discovery | Library |
| `/tus/` | POST | Create upload | Library |
| `/tus/{id}` | HEAD | Get offset (resume) | Library |
| `/tus/{id}` | PATCH | Upload chunk | Library |
| `/tus/{id}` | DELETE | Cancel/cleanup | Library |

### Supported TUS Extensions

The library includes these extensions out of the box:
- **creation** - Create new uploads via POST
- **termination** - Cancel uploads via DELETE  
- **expiration** - Automatic 24-hour session expiry
- **checksum** - Optional integrity verification
- **concatenation** - Parallel chunk uploads (if needed)

## Common Headers

All TUS requests include:
```
Tus-Resumable: 1.0.0
```

PIN-protected uploads MUST include:
```
X-Upload-Pin: {4-digit-pin}
```

## Endpoint Details

### OPTIONS /tus/

**Response** (200 OK):
```http
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
Tus-Extension: creation,termination,expiration
Tus-Max-Size: {available-storage-bytes}
```

### POST /tus/

**Request Headers**:
```http
Upload-Length: 1073741824
Upload-Metadata: filename dmlkZW8ubXA0,filetype dmlkZW8vbXA0
```

**Upload-Metadata** (Base64-encoded):
- `filename`: Original filename (required)
- `filetype`: MIME type (required)

**Response** (201 Created):
```http
Location: /tus/{upload-id}
Upload-Offset: 0
```

### HEAD /tus/{id}

**Response** (200 OK):
```http
Upload-Offset: 524288000
Upload-Length: 1073741824
Upload-Expires: {iso-datetime}
```

### PATCH /tus/{id}

**Request**:
```http
Upload-Offset: 524288000
Content-Type: application/offset+octet-stream
Content-Length: 5242880

{binary chunk data}
```

**Response** (204 No Content):
```http
Upload-Offset: 529530880
```

### DELETE /tus/{id}

**Response** (204 No Content)

---

## Error Handling

Standard TUS error responses (handled by library):

| Status | Meaning |
|--------|---------|
| 400 Bad Request | Malformed request |
| 404 Not Found | Upload ID not found |
| 409 Conflict | Offset mismatch |
| 410 Gone | Upload expired/cancelled |
| 413 Payload Too Large | Exceeds storage |

---

## PIN Protection (Custom Extension)

PIN verification is handled in our servlet wrapper BEFORE passing to tus-java-server:

```kotlin
class TusUploadServlet(private val tusService: TusFileUploadService) : HttpServlet() {
    
    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        // PIN check for non-OPTIONS requests
        if (req.method != "OPTIONS" && !verifyPin(req)) {
            resp.sendError(401, "PIN required")
            return
        }
        tusService.process(req, resp)  // Delegate to library
    }
}
```

---

## Additional Endpoints (Custom Servlets)

These endpoints are NOT handled by tus-java-server, but by our custom servlets:

| Endpoint | Method | Purpose | Servlet |
|----------|--------|---------|---------|
| `/api/status` | GET | Server status | ApiServlet |
| `/api/verify-pin` | POST | PIN verification | ApiServlet |
| `/*` | GET | Static assets (HTML/JS/CSS) | StaticAssetsServlet |

> **Note**: Legacy multipart upload endpoints (`/api/upload`, `/api/upload-resume`) have been removed. All uploads now use TUS protocol exclusively.


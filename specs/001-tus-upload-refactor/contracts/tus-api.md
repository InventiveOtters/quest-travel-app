# TUS API Contract

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-04  
**Protocol Version**: TUS 1.0.0

## Base URL

```
http://{device-ip}:{port}/tus/
```

Port: 8080 (with fallback to 8081, 8082, 8083)

## Common Headers

All TUS requests MUST include:
```
Tus-Resumable: 1.0.0
```

PIN-protected uploads MUST include:
```
X-Upload-Pin: {4-digit-pin}
```

## Endpoints

### OPTIONS /tus/

**Purpose**: Capability discovery

**Request**:
```http
OPTIONS /tus/ HTTP/1.1
Tus-Resumable: 1.0.0
```

**Response** (200 OK):
```http
HTTP/1.1 200 OK
Tus-Resumable: 1.0.0
Tus-Version: 1.0.0
Tus-Extension: creation,termination
Tus-Max-Size: {available-storage-bytes}
```

---

### POST /tus/

**Purpose**: Create new upload

**Request**:
```http
POST /tus/ HTTP/1.1
Tus-Resumable: 1.0.0
Upload-Length: 1073741824
Upload-Metadata: filename dmlkZW8ubXA0,filetype dmlkZW8vbXA0
Content-Length: 0
X-Upload-Pin: 1234
```

**Upload-Metadata** format: Base64-encoded key-value pairs, comma-separated
- `filename`: Original filename (required)
- `filetype`: MIME type (required)
- `fingerprint`: Client-generated hash for resume matching (optional)

**Response** (201 Created):
```http
HTTP/1.1 201 Created
Location: /tus/{upload-id}
Tus-Resumable: 1.0.0
Upload-Offset: 0
```

**Error Responses**:
- `401 Unauthorized`: PIN required or invalid
- `413 Payload Too Large`: File exceeds available storage
- `500 Internal Server Error`: Failed to create MediaStore entry

---

### HEAD /tus/{upload-id}

**Purpose**: Get upload offset (for resume)

**Request**:
```http
HEAD /tus/abc123-def456 HTTP/1.1
Tus-Resumable: 1.0.0
X-Upload-Pin: 1234
```

**Response** (200 OK):
```http
HTTP/1.1 200 OK
Tus-Resumable: 1.0.0
Upload-Offset: 524288000
Upload-Length: 1073741824
Cache-Control: no-store
```

**Error Responses**:
- `404 Not Found`: Upload ID not found or expired
- `410 Gone`: Upload was cancelled or cleaned up

---

### PATCH /tus/{upload-id}

**Purpose**: Upload chunk at offset

**Request**:
```http
PATCH /tus/abc123-def456 HTTP/1.1
Tus-Resumable: 1.0.0
Upload-Offset: 524288000
Content-Type: application/offset+octet-stream
Content-Length: 5242880
X-Upload-Pin: 1234

{binary chunk data}
```

**Response** (204 No Content):
```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Upload-Offset: 529530880
```

**Final Chunk Response** (when Upload-Offset == Upload-Length):
```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Upload-Offset: 1073741824
Upload-Complete: true
```

**Error Responses**:
- `409 Conflict`: Upload-Offset doesn't match server offset
- `404 Not Found`: Upload ID not found
- `413 Payload Too Large`: Storage exhausted mid-upload
- `460 Checksum Mismatch` (custom): File fingerprint changed (source file modified)

---

### DELETE /tus/{upload-id}

**Purpose**: Cancel upload and cleanup

**Request**:
```http
DELETE /tus/abc123-def456 HTTP/1.1
Tus-Resumable: 1.0.0
X-Upload-Pin: 1234
```

**Response** (204 No Content):
```http
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
```

---

## Legacy Endpoints (Preserved for Compatibility)

The following existing endpoints remain unchanged:
- `GET /api/status` - Server status
- `GET /api/files` - List uploaded files
- `GET /api/incomplete-uploads` - List resumable uploads (updated to use TUS data)
- `POST /api/verify-pin` - PIN verification

**Deprecated** (to be removed after migration):
- `POST /api/upload` - Old multipart upload
- `POST /api/upload-resume` - Old resume attempt


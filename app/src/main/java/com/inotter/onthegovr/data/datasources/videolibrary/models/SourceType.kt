package com.inotter.onthegovr.data.datasources.videolibrary.models

/**
 * Indicates the source from which a video was discovered.
 * Used for tracking and filtering videos by their origin.
 */
enum class SourceType {
    /**
     * Video discovered via Storage Access Framework (SAF).
     * User manually selected a folder to scan.
     */
    SAF,

    /**
     * Video discovered via MediaStore API.
     * Automatically discovered using READ_MEDIA_VIDEO permission.
     */
    MEDIASTORE
}


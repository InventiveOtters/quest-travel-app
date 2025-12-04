package com.inotter.travelcompanion.data.managers.LayoutDetectionManager

import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout

/**
 * Manager interface for detecting stereo layout (3D format) from video file names.
 * Uses filename heuristics to identify SBS (side-by-side), TAB (top-and-bottom),
 * and their half-resolution variants (HSBS, HTAB).
 */
interface LayoutDetectionManager {

    /**
     * Detects the stereo layout from a video file name.
     *
     * @param fileName The video file name to analyze
     * @return The detected stereo layout, or StereoLayout.Unknown if no pattern matches
     */
    fun detect(fileName: String?): StereoLayout
}


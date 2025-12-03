package com.inotter.travelcompanion.domain.layout

import com.inotter.travelcompanion.data.db.StereoLayout

/**
 * Detects stereo layout (3D format) from video file names.
 * Uses filename heuristics to identify SBS (side-by-side), TAB (top-and-bottom),
 * and their half-resolution variants (HSBS, HTAB).
 */
object StereoLayoutDetector {
  private val regexSbs = Regex("\\b(h?sbs|side[-_ ]?by[-_ ]?side)\\b", RegexOption.IGNORE_CASE)
  private val regexTab = Regex("\\b(h?tab|top[-_ ]?and[-_ ]?bottom)\\b", RegexOption.IGNORE_CASE)

  /**
   * Detects the stereo layout from a video file name.
   *
   * @param fileName The video file name to analyze
   * @return The detected stereo layout, or StereoLayout.Unknown if no pattern matches
   */
  fun detect(fileName: String?): StereoLayout {
    val n = fileName ?: return StereoLayout.Unknown
    return when {
      regexSbs.containsMatchIn(n) -> if (n.contains("hsbs", true)) StereoLayout.HSBS else StereoLayout.SBS
      regexTab.containsMatchIn(n) -> if (n.contains("htab", true)) StereoLayout.HTAB else StereoLayout.TAB
      else -> StereoLayout.Unknown
    }
  }
}


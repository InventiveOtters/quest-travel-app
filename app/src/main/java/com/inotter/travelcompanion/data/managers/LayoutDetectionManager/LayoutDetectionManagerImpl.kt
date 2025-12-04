package com.inotter.travelcompanion.data.managers.LayoutDetectionManager

import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LayoutDetectionManager] that detects stereo layout from video file names.
 */
@Singleton
class LayoutDetectionManagerImpl @Inject constructor() : LayoutDetectionManager {

    companion object {
        private val regexSbs = Regex("\\b(h?sbs|side[-_ ]?by[-_ ]?side)\\b", RegexOption.IGNORE_CASE)
        private val regexTab = Regex("\\b(h?tab|top[-_ ]?and[-_ ]?bottom)\\b", RegexOption.IGNORE_CASE)
    }

    override fun detect(fileName: String?): StereoLayout {
        val n = fileName ?: return StereoLayout.Unknown
        return when {
            regexSbs.containsMatchIn(n) -> if (n.contains("hsbs", true)) StereoLayout.HSBS else StereoLayout.SBS
            regexTab.containsMatchIn(n) -> if (n.contains("htab", true)) StereoLayout.HTAB else StereoLayout.TAB
            else -> StereoLayout.Unknown
        }
    }
}


package com.inotter.travelcompanion.data.models

/**
 * Viewing mode preference for the app.
 * Determines whether the user watches videos in a 2D floating panel or full immersive VR.
 */
enum class ViewingMode {
    /**
     * 2D Panel Mode - Watch in a floating panel.
     * Great for multitasking in Meta Quest Home.
     */
    PANEL_2D,

    /**
     * Immersive VR Mode - Full immersive theatre experience.
     * Feel like you're there with a large virtual screen.
     */
    IMMERSIVE;

    companion object {
        /**
         * Default viewing mode for new users.
         */
        val DEFAULT = IMMERSIVE

        /**
         * Parse viewing mode from string, returning default if invalid.
         */
        fun fromString(value: String?): ViewingMode {
            return when (value) {
                PANEL_2D.name -> PANEL_2D
                IMMERSIVE.name -> IMMERSIVE
                else -> DEFAULT
            }
        }
    }
}


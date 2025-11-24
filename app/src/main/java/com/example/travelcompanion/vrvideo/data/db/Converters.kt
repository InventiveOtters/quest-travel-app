package com.example.travelcompanion.vrvideo.data.db

import androidx.room.TypeConverter

class Converters {
  @TypeConverter fun fromStereoLayout(value: StereoLayout?): String? = value?.name
  @TypeConverter fun toStereoLayout(value: String?): StereoLayout? =
      value?.let { runCatching { StereoLayout.valueOf(it) }.getOrDefault(StereoLayout.Unknown) }

  @TypeConverter fun fromThumbStatus(value: ThumbnailGenerationStatus?): String? = value?.name
  @TypeConverter fun toThumbStatus(value: String?): ThumbnailGenerationStatus? =
      value?.let { runCatching { ThumbnailGenerationStatus.valueOf(it) }.getOrNull() }
}


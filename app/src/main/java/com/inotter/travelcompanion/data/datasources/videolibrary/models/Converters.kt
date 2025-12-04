package com.inotter.travelcompanion.data.datasources.videolibrary.models

import androidx.room.TypeConverter

class Converters {
  @TypeConverter fun fromStereoLayout(value: StereoLayout?): String? = value?.name
  @TypeConverter fun toStereoLayout(value: String?): StereoLayout? =
      value?.let { runCatching { StereoLayout.valueOf(it) }.getOrDefault(StereoLayout.Unknown) }

  @TypeConverter fun fromThumbStatus(value: ThumbnailGenerationStatus?): String? = value?.name
  @TypeConverter fun toThumbStatus(value: String?): ThumbnailGenerationStatus? =
      value?.let { runCatching { ThumbnailGenerationStatus.valueOf(it) }.getOrNull() }

  @TypeConverter fun fromSourceType(value: SourceType?): String? = value?.name
  @TypeConverter fun toSourceType(value: String?): SourceType? =
      value?.let { runCatching { SourceType.valueOf(it) }.getOrDefault(SourceType.SAF) }
}


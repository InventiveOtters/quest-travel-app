plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.meta.spatial.plugin)
  alias(libs.plugins.jetbrains.kotlin.plugin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.inotter.onthegovr"
  // compileSdk 35 required for Media3 1.9.0-rc01 and FFmpeg extension AARs
  compileSdk = 35

  defaultConfig {
    applicationId = "com.inotter.onthegovr"
    minSdk = 32
    // HorizonOS is Android 14 (API level 34)
    //noinspection OldTargetApi,ExpiredTargetSdkVersion
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Target 64-bit only to reduce APK size
    ndk {
      abiFilters.add("arm64-v8a")
    }
  }

  packaging {
    resources.excludes.add("META-INF/LICENSE")
    resources.excludes.add("META-INF/NOTICE.md")
    resources.excludes.add("META-INF/LICENSE.md")
    resources.excludes.add("META-INF/DEPENDENCIES")
  }

  lint { abortOnError = false }

  // Define flavor dimensions
  flavorDimensions += "mode"

  productFlavors {
    create("immersive") {
      dimension = "mode"
      // Immersive VR mode - full spatial experience
      buildConfigField("Boolean", "IS_IMMERSIVE", "true")
    }
    create("panel") {
      dimension = "mode"
      // 2D panel mode - standard Android UI
      buildConfigField("Boolean", "IS_IMMERSIVE", "false")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures {
    buildConfig = true
    compose = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

// KSP configuration (Room schema export)
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}


//noinspection UseTomlInstead
dependencies {
  implementation(libs.androidx.core.ktx)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)

  // This project incorporates the Meta Spatial SDK, licensed under the Meta Platforms Technologies
  // SDK License Agreement available at https://developers.meta.com/horizon/licenses/oculussdk/
  // Meta Spatial SDK libs
  implementation(libs.meta.spatial.sdk.base)
  implementation(libs.meta.spatial.sdk.ovrmetrics)
  implementation(libs.meta.spatial.sdk.toolkit)
  implementation(libs.meta.spatial.sdk.physics)
  implementation(libs.meta.spatial.sdk.vr)
  implementation(libs.meta.spatial.sdk.isdk)
  implementation(libs.meta.spatial.sdk.compose)
  implementation(libs.meta.spatial.sdk.castinputforward)
  implementation(libs.meta.spatial.sdk.hotreload)
  implementation(libs.meta.spatial.sdk.datamodelinspector)
  implementation(libs.meta.spatial.sdk.uiset)

  // Compose Dependencies
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.navigation.compose)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)

  // Media3/ExoPlayer
  // Using pre-built AARs from Just Player for FFmpeg decoder with full codec support
  // (TrueHD, AC3, EAC3, DTS, DTS-HD, etc.)
  // Source: https://github.com/moneytoo/Player/blob/master/app/build.gradle
  // lib-exoplayer-release.aar replaces the standard media3-exoplayer dependency
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("lib-*.aar"))))

  // Core Media3 modules required by the custom lib-exoplayer-release.aar
  // Must match the modules used by Just Player
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.decoder)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.container)
  implementation(libs.androidx.media3.extractor)

  // Do NOT include libs.androidx.media3.exoplayer - we use lib-exoplayer-release.aar instead
  // Exclude media3-exoplayer from all dependencies that pull it transitively
  implementation(libs.androidx.media3.ui) {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
  }
  implementation(libs.androidx.media3.exoplayer.dash) {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
  }

  // Room (with KSP for compiler)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // WorkManager
  implementation(libs.androidx.work.runtime.ktx)

  // DocumentFile (SAF)
  implementation(libs.androidx.documentfile)

  // Jetty Embedded (HTTP server for WiFi transfer - replaces NanoHTTPD)
  // Using Jetty 9.4.x for Android compatibility (Jetty 11+ uses Java 9+ APIs not available on Android)
  implementation("org.eclipse.jetty:jetty-server:9.4.54.v20240208")
  implementation("org.eclipse.jetty:jetty-servlet:9.4.54.v20240208")
  implementation("org.eclipse.jetty.websocket:websocket-server:9.4.54.v20240208")
  implementation("org.eclipse.jetty.websocket:websocket-servlet:9.4.54.v20240208")

  // TUS Protocol Server (resumable uploads)
  // Using 1.0.0-2.1 for javax.servlet compatibility with Jetty 9.4.x
  implementation("me.desair.tus:tus-java-server:1.0.0-2.1")

  // OkHttp (WebSocket for sync commands)
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // Gson (JSON serialization for sync protocol)
  implementation("com.google.code.gson:gson:2.10.1")

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.hilt.work)
  ksp(libs.hilt.work.compiler)

  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

val projectDir = layout.projectDirectory
val sceneDirectory = projectDir.dir("scenes")

// Determine Meta Spatial Editor CLI path based on OS
val spatialCliPath: String = when {
  org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
    "/Applications/Meta Spatial Editor.app/Contents/MacOS/CLI"
  org.gradle.internal.os.OperatingSystem.current().isWindows ->
    "D:\\Meta Spatial Editor\\v11\\Resources\\CLI.exe"
  else ->
    "/Applications/Meta Spatial Editor.app/Contents/MacOS/CLI" // Default to macOS
}

spatial {
  allowUsageDataCollection.set(true)
  scenes {
    // Meta Spatial Editor CLI path (auto-detected based on OS)
    cliPath.set(spatialCliPath)

    exportItems {
      item {
        projectPath.set(sceneDirectory.file("Main.metaspatial"))
        outputPath.set(projectDir.dir("src/main/assets/scenes"))
      }
    }
    hotReload {
      appPackage.set("com.inotter.onthegovr")
      appMainActivity.set(".ImmersiveActivity")
      assetsDir.set(File("src/main/assets"))
    }
  }
}

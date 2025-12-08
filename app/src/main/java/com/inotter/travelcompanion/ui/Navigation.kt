package com.inotter.travelcompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inotter.travelcompanion.ui.core.components.IncompleteUploadsDialog
import com.inotter.travelcompanion.ui.library.AddFolderPanel
import com.inotter.travelcompanion.ui.library.LibraryScreen
import com.inotter.travelcompanion.ui.library.LibraryViewModel
import com.inotter.travelcompanion.ui.library.ManageSourcesScreen
import com.inotter.travelcompanion.ui.onboarding.OnboardingScreen
import com.inotter.travelcompanion.ui.onboarding.OnboardingViewModel
import com.inotter.travelcompanion.ui.player.PlayerScreen
import com.inotter.travelcompanion.ui.player.PlayerViewModel
import com.inotter.travelcompanion.ui.settings.SettingsScreen
import com.inotter.travelcompanion.ui.settings.SettingsViewModel
import com.inotter.travelcompanion.ui.transfer.IncompleteUploadsViewModel
import com.inotter.travelcompanion.ui.transfer.TransferViewModel
import com.inotter.travelcompanion.ui.transfer.WiFiTransferScreen
import com.inotter.travelcompanion.ui.sync.SyncScreen
import com.inotter.travelcompanion.ui.sync.SyncClientPlayerScreen
import com.inotter.travelcompanion.ui.sync.rememberSyncViewModel

/**
 * Navigation host for VR UI.
 * Manages navigation between Onboarding → Library → Player screens.
 * Also handles incomplete uploads detection and dialog on startup.
 *
 * @param onLaunchImmersive Callback to launch immersive mode (transitions to ImmersiveActivity)
 * @param onLaunchPanel Callback to ensure we're in panel mode (used from settings)
 */
@Composable
fun VRNavigationHost(
    onLaunchImmersive: () -> Unit = {},
    onLaunchPanel: () -> Unit = {},
) {
  val navController = rememberNavController()
  val libraryViewModel: LibraryViewModel = viewModel()
  val playerViewModel: PlayerViewModel = viewModel()
  val settingsViewModel: SettingsViewModel = viewModel()
  val transferViewModel: TransferViewModel = viewModel()
  val onboardingViewModel: OnboardingViewModel = viewModel()
  val incompleteUploadsViewModel: IncompleteUploadsViewModel = viewModel()

  // Create a shared SyncViewModel instance for sync-related screens
  val context = androidx.compose.ui.platform.LocalContext.current
  val syncViewModel = rememberSyncViewModel(context)

  // Observe incomplete uploads state
  val showIncompleteUploadsDialog by incompleteUploadsViewModel.showDialog.collectAsState()
  val incompleteUploads by incompleteUploadsViewModel.incompleteUploads.collectAsState()
  val orphanedEntries by incompleteUploadsViewModel.orphanedEntries.collectAsState()

  // Show incomplete uploads dialog if needed (includes orphaned MediaStore entries)
  if (showIncompleteUploadsDialog && (incompleteUploads.isNotEmpty() || orphanedEntries.isNotEmpty())) {
    IncompleteUploadsDialog(
      incompleteUploads = incompleteUploads,
      orphanedEntries = orphanedEntries,
      onCleanUp = { incompleteUploadsViewModel.cleanUpIncompleteUploads() },
      onContinueUploading = {
        incompleteUploadsViewModel.onContinueUploading()
        navController.navigate("wifiTransfer")
      },
      onDismiss = { incompleteUploadsViewModel.dismissDialog() }
    )
  }

  // Determine start destination based on onboarding status
  val startDestination = remember {
    if (onboardingViewModel.shouldShowOnboarding()) "onboarding" else "library"
  }

  NavHost(navController = navController, startDestination = startDestination) {
    composable("onboarding") {
      OnboardingScreen(
          viewModel = onboardingViewModel,
          onComplete = {
            // User selected 2D Panel Mode - navigate to library
            navController.navigate("library") {
              popUpTo("onboarding") { inclusive = true }
            }
          },
          onCompleteImmersive = {
            // User selected Immersive Mode - launch ImmersiveActivity
            onLaunchImmersive()
          },
          onUseSaf = {
            navController.navigate("addFolder") {
              popUpTo("onboarding") { inclusive = true }
            }
          },
      )
    }
    composable("library") {
      LibraryScreen(
          viewModel = libraryViewModel,
          onVideoSelected = { video -> navController.navigate("player/${video.id}") },
          onAddFolder = { navController.navigate("addFolder") },
          onManageSources = { navController.navigate("manageSources") },
          onSettings = { navController.navigate("settings") },
          onWifiTransfer = { navController.navigate("wifiTransfer") },
	          onSync = { navController.navigate("sync") },
      )
    }

    composable("addFolder") {
      AddFolderPanel(
          viewModel = libraryViewModel,
          onFolderAdded = { navController.popBackStack() },
      )
    }

    composable("manageSources") {
      ManageSourcesScreen(
          viewModel = libraryViewModel,
          onAddFolder = { navController.navigate("addFolder") },
          onBack = { navController.popBackStack() },
      )
    }

    composable("settings") {
      SettingsScreen(
          viewModel = settingsViewModel,
          onBack = { navController.popBackStack() },
          onSwitchToImmersive = onLaunchImmersive,
          onSwitchToPanel = onLaunchPanel,
      )
    }

    composable("wifiTransfer") {
      WiFiTransferScreen(
          viewModel = transferViewModel,
          onBack = { navController.popBackStack() },
      )
    }

	    	    composable("sync") {
	    	      SyncScreen(
	    	          syncViewModel = syncViewModel,
	    	          onBack = { navController.popBackStack() },
	    	          currentVideo = playerViewModel.getCurrentVideo(),
	    	          onJoinedSession = {
	    	              // Navigate to sync client player when joined as client
	    	              navController.navigate("syncClientPlayer") {
	    	                  popUpTo("sync") { inclusive = true }
	    	              }
	    	          }
	    	      )
	    	    }

	    	    composable("syncAuto") {
	    	      SyncScreen(
	    	          syncViewModel = syncViewModel,
	    	          onBack = { navController.popBackStack() },
	    	          currentVideo = playerViewModel.getCurrentVideo(),
	    	          autoCreateOnEnter = true,
	    	          onJoinedSession = {
	    	              // Navigate to sync client player when joined as client
	    	              navController.navigate("syncClientPlayer") {
	    	                  popUpTo("syncAuto") { inclusive = true }
	    	              }
	    	          }
	    	      )
	    	    }

	    	    composable("syncClientPlayer") {
	    	      // Player screen for sync client (streaming from master)
	    	      // Uses the shared SyncViewModel's PlaybackCore instance
	    	      SyncClientPlayerScreen(
	    	          syncViewModel = syncViewModel,
	    	          onBack = {
	    	              navController.navigate("library") {
	    	                  popUpTo("library") { inclusive = false }
	    	              }
	    	          }
	    	      )
	    	    }

    composable(
        route = "player/{videoId}",
        arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
    ) { backStackEntry ->
      val videoId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
      val video = libraryViewModel.videos.value.find { it.id == videoId } ?: return@composable

      PlayerScreen(
          viewModel = playerViewModel,
          video = video,
	          onBack = { navController.popBackStack() },
	          onHostSession = { navController.navigate("syncAuto") },
      )
    }
  }
}


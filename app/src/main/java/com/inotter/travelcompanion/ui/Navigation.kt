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
import com.inotter.travelcompanion.ui.components.IncompleteUploadsDialog
import com.inotter.travelcompanion.ui.screens.AddFolderPanel
import com.inotter.travelcompanion.ui.screens.LibraryScreen
import com.inotter.travelcompanion.ui.screens.ManageSourcesScreen
import com.inotter.travelcompanion.ui.screens.OnboardingScreen
import com.inotter.travelcompanion.ui.screens.PlayerScreen
import com.inotter.travelcompanion.ui.screens.SettingsScreen
import com.inotter.travelcompanion.ui.screens.WiFiTransferScreen
import com.inotter.travelcompanion.ui.viewmodel.IncompleteUploadsViewModel
import com.inotter.travelcompanion.ui.viewmodel.LibraryViewModel
import com.inotter.travelcompanion.ui.viewmodel.OnboardingViewModel
import com.inotter.travelcompanion.ui.viewmodel.PlayerViewModel
import com.inotter.travelcompanion.ui.viewmodel.SettingsViewModel
import com.inotter.travelcompanion.ui.viewmodel.TransferViewModel

/**
 * Navigation host for VR UI.
 * Manages navigation between Onboarding → Library → Player screens.
 * Also handles incomplete uploads detection and dialog on startup.
 */
@Composable
fun VRNavigationHost() {
  val navController = rememberNavController()
  val libraryViewModel: LibraryViewModel = viewModel()
  val playerViewModel: PlayerViewModel = viewModel()
  val settingsViewModel: SettingsViewModel = viewModel()
  val transferViewModel: TransferViewModel = viewModel()
  val onboardingViewModel: OnboardingViewModel = viewModel()
  val incompleteUploadsViewModel: IncompleteUploadsViewModel = viewModel()

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
            navController.navigate("library") {
              popUpTo("onboarding") { inclusive = true }
            }
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
      )
    }

    composable("wifiTransfer") {
      WiFiTransferScreen(
          viewModel = transferViewModel,
          onBack = { navController.popBackStack() },
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
      )
    }
  }
}


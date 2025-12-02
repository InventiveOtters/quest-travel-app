package com.example.travelcompanion.vrvideo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.travelcompanion.vrvideo.ui.screens.AddFolderPanel
import com.example.travelcompanion.vrvideo.ui.screens.LibraryScreen
import com.example.travelcompanion.vrvideo.ui.screens.ManageSourcesScreen
import com.example.travelcompanion.vrvideo.ui.screens.OnboardingScreen
import com.example.travelcompanion.vrvideo.ui.screens.PlayerScreen
import com.example.travelcompanion.vrvideo.ui.screens.SettingsScreen
import com.example.travelcompanion.vrvideo.ui.screens.WiFiTransferScreen
import com.example.travelcompanion.vrvideo.ui.viewmodel.LibraryViewModel
import com.example.travelcompanion.vrvideo.ui.viewmodel.OnboardingViewModel
import com.example.travelcompanion.vrvideo.ui.viewmodel.PlayerViewModel
import com.example.travelcompanion.vrvideo.ui.viewmodel.SettingsViewModel
import com.example.travelcompanion.vrvideo.ui.viewmodel.TransferViewModel

/**
 * Navigation host for VR UI.
 * Manages navigation between Onboarding → Library → Player screens.
 */
@Composable
fun VRNavigationHost() {
  val navController = rememberNavController()
  val libraryViewModel: LibraryViewModel = viewModel()
  val playerViewModel: PlayerViewModel = viewModel()
  val settingsViewModel: SettingsViewModel = viewModel()
  val transferViewModel: TransferViewModel = viewModel()
  val onboardingViewModel: OnboardingViewModel = viewModel()

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


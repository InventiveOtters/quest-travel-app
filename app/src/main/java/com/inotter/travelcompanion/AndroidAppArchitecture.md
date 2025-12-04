# Overall Architecture

We use MVVM with Jetpack ViewModel and StateFlow/Compose.

We **don't** use clean architecture (no domain/use-case layer).

# Data Layer Concepts

The data layer is responsible for fetching data from remote sources and transforming it into models that can be used by the UI layer.

- **Data Sources**: Responsible for providing raw data. Should be generic and versatile, exposing simple APIs for CRUD operations.
    - Firebase Firestore, Firebase Auth, Firebase Storage, etc. are considered data sources. They don't need to be wrapped in another data source.
    - Room Database and DAOs are considered data sources.
- **Repositories**: Abstract the data source. Combine multiple data sources if needed (e.g., remote + local cache).
- **Managers**: Abstract common business logic that is not specific to a particular feature or used across multiple features.
    - Note: We use "Manager" instead of "Service" to avoid confusion with Android's `Service` component (foreground/background services).

## Folder Structure

```
data/
├── datasources/
│   └── <DataSourceName>/
│       ├── <DataSourceName>.kt           # interface
│       ├── <DataSourceName>Impl.kt
│       └── models/
│           └── <ModelName>.kt
├── repositories/
│   └── <RepositoryName>/
│       ├── <RepositoryName>.kt           # interface
│       ├── <RepositoryName>Impl.kt
│       └── models/
│           └── <ModelName>.kt
└── managers/
    └── <ManagerName>/
        ├── <ManagerName>.kt              # interface
        └── <ManagerName>Impl.kt
```

# UI Layer Concepts

We use Jetpack ViewModel with StateFlow to manage the state of the UI. UI is built with Jetpack Compose.

## Folder Structure

```
ui/
├── core/
│   └── components/
│       └── <SharedComposables>.kt
└── <feature_name>/
    ├── <FeatureName>ViewModel.kt
    ├── <FeatureName>UiState.kt
    └── <FeatureName>Screen.kt
```

# Android Components

Android-specific components (Activities, foreground Services, BroadcastReceivers) live at the package root level alongside `MainActivity.kt`.

```
com/inotter/travelcompanion/
├── MainActivity.kt
├── ImmersiveActivity.kt
├── TransferService.kt              # Android foreground service
├── TravelCompanionApp.kt           # Hilt Application class
├── data/
├── ui/
└── workers/
```

# Workers

WorkManager workers live in a dedicated `workers/` folder at the package root level.

```
workers/
├── IndexWorker.kt
└── MediaStoreScanWorker.kt
```

# Dependency Injection

We use Hilt for dependency injection. All data sources, repositories, managers, and ViewModels are provided via Hilt modules.


<!--
  SYNC IMPACT REPORT
  ==================
  Version Change: N/A → 1.0.0 (initial adoption)

  Added Sections:
  - Core Principles (5 principles derived from AndroidAppArchitecture.md)
  - Architecture Constraints (technology stack requirements)
  - Development Standards (code quality and documentation)
  - Governance (amendment procedures)

  Templates Requiring Updates:
  - .specify/templates/plan-template.md: ✅ Already compatible (Constitution Check section exists)
  - .specify/templates/spec-template.md: ✅ Already compatible (no constitution-specific requirements)
  - .specify/templates/tasks-template.md: ✅ Already compatible (no constitution-specific task types)

  Follow-up TODOs: None
-->

# TravelCompanion Constitution

## Core Principles

### I. MVVM Architecture (NON-NEGOTIABLE)

All features MUST follow the MVVM (Model-View-ViewModel) pattern using Jetpack ViewModel and StateFlow/Compose:

- **UI Layer**: Jetpack Compose screens with dedicated ViewModel and UiState per feature
- **ViewModel**: MUST use StateFlow for state management; MUST NOT contain Android framework dependencies
- **No Clean Architecture**: The domain/use-case layer is explicitly prohibited; business logic resides in Managers or Repositories
- **State**: All UI state MUST be represented in a single `*UiState` data class per feature

**Rationale**: Consistent architecture reduces cognitive load and enables predictable feature development.

### II. Layered Data Architecture

Data layer components MUST follow the established hierarchy:

- **DataSources**: Provide raw data via simple CRUD APIs (Room DAOs, Firebase, etc.)
- **Repositories**: Abstract data sources; combine remote + local when needed; interface + Impl pattern required
- **Managers**: Encapsulate cross-feature business logic; use "Manager" naming (not "Service") to avoid confusion with Android Services

Folder structure MUST follow:
```
data/
├── datasources/<Name>/ (interface, impl, models/)
├── repositories/<Name>/ (interface, impl, models/)
└── managers/<Name>/ (interface, impl)
```

**Rationale**: Separation of concerns enables testability and reduces coupling between layers.

### III. Documentation Standards

All public interfaces MUST be documented:

- **Public classes/interfaces**: MUST have KDoc with description of purpose
- **Public methods**: MUST have KDoc with `@param` and `@return` documentation
- **Complex logic**: MUST include inline comments explaining non-obvious decisions
- **Architecture decisions**: MUST be documented in relevant `.md` files

**Rationale**: Documentation enables maintainability and reduces onboarding friction for future developers.

### IV. Hilt Dependency Injection

All dependencies MUST be provided via Hilt:

- DataSources, Repositories, Managers, and ViewModels MUST be provided through Hilt modules
- Constructor injection is preferred; field injection MUST be avoided except in Android components
- Scoping MUST be explicit (`@Singleton`, `@ViewModelScoped`, etc.)

**Rationale**: Centralized DI configuration ensures consistent dependency management and enables testing.

### V. Simplicity and Maintainability

Code MUST prioritize maintainability over cleverness:

- **YAGNI**: Do not implement features "just in case"; implement when needed
- **Single Responsibility**: Each class/function MUST have one clear purpose
- **Readable Code**: Prefer explicit over implicit; use meaningful names
- **Minimal Abstractions**: Add abstraction layers only when concrete need exists
- **Error Handling**: All errors MUST be handled explicitly; fail fast with clear messages

**Rationale**: Simple code is easier to understand, test, and maintain over time.

## Architecture Constraints

### Technology Stack

| Component | Requirement |
|-----------|-------------|
| **Language** | Kotlin 1.9+ (Android) |
| **UI Framework** | Jetpack Compose |
| **State Management** | StateFlow via ViewModel |
| **Dependency Injection** | Hilt |
| **Local Storage** | Room Database |
| **Background Work** | WorkManager (workers in `workers/` folder) |
| **Target Platform** | Meta Quest / HorizonOS (Android 14, API 34) |

### Android Components

Android-specific components (Activities, Services, BroadcastReceivers) MUST reside at the package root level:
```
com/inotter/travelcompanion/
├── MainActivity.kt
├── *Activity.kt
├── *Service.kt
├── TravelCompanionApp.kt
├── data/
├── ui/
└── workers/
```

## Development Standards

### Code Quality Gates

- All PRs MUST pass lint checks before merge
- Public API changes MUST include documentation updates
- New features MUST follow established folder structure patterns
- Architecture violations MUST be flagged during code review

### Naming Conventions

- **Features**: `<FeatureName>ViewModel`, `<FeatureName>UiState`, `<FeatureName>Screen`
- **Data Components**: Interface + `Impl` suffix (e.g., `UserRepository`, `UserRepositoryImpl`)
- **Managers**: Use "Manager" suffix for cross-feature business logic (e.g., `TransferManager`)
- **Workers**: Use "Worker" suffix (e.g., `IndexWorker`, `MediaStoreScanWorker`)

## Governance

This constitution supersedes all conflicting practices. Amendments require:

1. **Documentation**: Clear description of change and rationale
2. **Impact Assessment**: List of affected code/patterns
3. **Migration Plan**: How existing code will be updated (if applicable)
4. **Version Bump**: Follow semantic versioning (MAJOR.MINOR.PATCH)

Reference `app/src/main/java/com/inotter/travelcompanion/AndroidAppArchitecture.md` as the authoritative architecture document. This constitution codifies and enforces those patterns.

**Version**: 1.0.0 | **Ratified**: 2024-12-04 | **Last Amended**: 2024-12-04

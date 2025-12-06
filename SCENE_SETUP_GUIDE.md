# Scene Setup Guide for Travel Companion VR

This guide explains how to set up the spatial scenes in Meta Spatial Editor for the Travel Companion offline movie player.

## Architecture Overview

For **optimal performance** on Meta Quest, we use a **single composition with multiple environment assets** approach rather than separate scene compositions. This avoids expensive scene reloading during environment switches.

### Why Single Composition?
- **Faster switching**: Toggling visibility is instant vs reloading entire GLXF files
- **Shared panels**: Library and Controls panels remain in place during switches
- **Lower memory overhead**: Assets can be shared between environments
- **Simpler state management**: No need to re-initialize panels on scene change

## Files to Create in Spatial Editor

### 1. Update Existing Composition (`scenes/Composition.glxf`)

Your existing composition already has:
- `VRVideoLibraryPanel` - Video library/player panel
- `ControlsPanel` - Playback controls with settings
- `Environment` - The default collab_room environment

### 2. Create Additional Environment Assets

Create these GLTF files and add them to the composition:

#### a. `cinema_dark.gltf` - Dark Cinema Environment
A dark cinema-style environment optimized for movie watching:
- **Walls**: Dark matte material (almost black)
- **Floor**: Dark carpet texture
- **Ceiling**: Black with subtle ambient occlusion
- **No windows or bright surfaces**
- **Optional**: Subtle floor lighting strips

Export as `scenes/cinema_dark.gltf`

#### b. Keep existing `collab_room.gltf`
This serves as the "Collab Room" environment (already exists).

### 3. Update Composition in Spatial Editor

Open `scenes/Composition.glxf` in Meta Spatial Editor and:

1. **Import the new environment asset**:
   - File > Import Asset > Select `cinema_dark.gltf`

2. **Add new environment node**:
   - Create a new node named exactly: `Environment_CinemaDark`
   - Assign the `cinema_dark` asset to this node
   - Position at origin (0, 0, 0)
   - Set initial visibility to `false` (hidden by default)

3. **Verify existing nodes**:
   - `Environment` - Should reference `collab_room` asset
   - `VRVideoLibraryPanel` - Video panel with `@id/library_panel`
   - `ControlsPanel` - Controls panel with `@id/controls_panel`

### 4. Node Naming Convention

The code expects these exact node names:

| Node Name | Purpose | Initial Visibility |
|-----------|---------|-------------------|
| `Environment` | Default collab room | `true` |
| `Environment_CinemaDark` | Dark cinema | `false` |
| `VRVideoLibraryPanel` | Video library panel | `true` |
| `ControlsPanel` | Playback controls | `false` |

### 5. VOID Environment

The "Void" environment option doesn't require a scene file - it simply hides all environment meshes, leaving only the panels visible in empty space. This is handled in code.

## Code Integration

The environments are automatically detected and registered in `ImmersiveActivity.kt`:

```kotlin
// In initializeTheatreFromGLXF()
val environmentEntity = composition.tryGetNodeByName("Environment")?.entity
theatreViewModel.initializeEntities(scene, libraryPanel, controlsPanel, environmentEntity)

// Register additional environments
composition.tryGetNodeByName("Environment_CinemaDark")?.entity?.let { entity ->
    theatreViewModel.registerEnvironment(EnvironmentType.CINEMA_DARK, entity)
}
```

## Controls Panel Features

The updated controls panel now includes:

### Settings Section (toggle with sun/moon icon)
1. **Lighting Slider**: Adjusts scene ambient lighting
   - Left (🌙): Movie mode - dark environment, only panels lit
   - Right (☀️): Full brightness - normal scene lighting

2. **Environment Selector**: Horizontal chips to switch environments
   - Collab Room (default)
   - Dark Cinema
   - Void (empty space)

### Playback Controls
- Play/Pause
- Seek slider
- Rewind/Fast Forward (10s)
- Restart
- Mute toggle

## Lighting System

The `SceneLightingManager` controls scene lighting via:

```kotlin
scene.setLightingEnvironment(
    ambientColor = Vector3(...),
    sunColor = Vector3(...),
    sunDirection = Vector3(...),
    environmentIntensity = Float
)
```

Lighting intensity ranges from:
- **0.0** (Movie Mode): All ambient/sun light at 0, only panels visible
- **1.0** (Full Brightness): Normal scene lighting

## Adding New Environments

To add a new environment:

1. **Create the GLTF asset** in your 3D editor (Blender, etc.)

2. **Add to EnvironmentType enum** in `EnvironmentSettings.kt`:
   ```kotlin
   enum class EnvironmentType(...) {
       // existing...
       NEW_ENVIRONMENT("Display Name", environmentAsset = "asset_name"),
   }
   ```

3. **Add node in Spatial Editor** with name `Environment_NewEnvironment`

4. **Register in ImmersiveActivity**:
   ```kotlin
   composition.tryGetNodeByName("Environment_NewEnvironment")?.entity?.let { 
       theatreViewModel.registerEnvironment(EnvironmentType.NEW_ENVIRONMENT, it)
   }
   ```

## Performance Tips

1. **Optimize environment meshes**: Keep polygon count low for VR performance
2. **Use texture atlases**: Reduce draw calls
3. **Avoid dynamic lights**: Baked lighting is more performant
4. **Test on device**: Always test environment switching on actual Quest hardware

# Chicken Warden (鸡管者) — Implementierung

```yaml
id: youzaiworldcore-chicken-warden
summary: >
  Merged the Bedrock resource pack "鸡管者" (Chicken Warden) into the YouzaiWorldCore Fabric mod.
  Replaces the vanilla Warden entity rendering with a chicken-like model using GeckoLib 5.
status: merged
date: 2026-06-27
```

## Merged Files

### Resource files (copied from Bedrock pack → new GeckoLib 5 paths)
| Type | Files | Target Path |
|------|-------|-------------|
| Model | 1 (.geo.json) | `assets/<mod>/geckolib/models/entity/warden.geo.json` |
| Animation | 1 (.animation.json) | `assets/<mod>/geckolib/animations/entity/warden.animation.json` |
| Controller | 1 (.json) | `assets/<mod>/controllers/warden.animation_controllers.json` |
| Textures | 8 (.png) | `assets/<mod>/textures/entity/warden/` |
| Particle | 6 (.png) | `assets/<mod>/textures/particle/warden/` |
| Sounds | 87 (.ogg) | `assets/minecraft/sounds/mob/warden/` |

### Java Classes (7 + 1 = 8 files)
| Class | Package | Base Class | Notes |
|-------|---------|------------|-------|
| `ChickenWardenRenderer` | `renderer/entity` | `GeoReplacedEntityRenderer` | Replaces vanilla Warden rendering |
| `ChickenWardenModel` | `renderer/entity/model` | `GeoModel<ChickenWardenAnimatable>` | Points to `.geo.json` + `chicken.png` |
| `ChickenWardenAnimatable` | `renderer/entity` | `GeoAnimatable` | Drives animation controllers |
| `BioluminescentLayer` | `renderer/entity/layer` | `GeoRenderLayer` | Emissive overlay |
| `Spots1Layer` | `renderer/entity/layer` | `GeoRenderLayer` | Pulsating spots 1 |
| `Spots2Layer` | `renderer/entity/layer` | `GeoRenderLayer` | Pulsating spots 2 |
| `TendrilsLayer` | `renderer/entity/layer` | `GeoRenderLayer` | Tendril overlay |
| `HeartLayer` | `renderer/entity/layer` | `GeoRenderLayer` | Heart overlay |

### Configuration Changes
- `build.gradle`: Added GeckoLib 5 Cloudsmith repo + `com.geckolib:geckolib-fabric-26.2:5.5.1`
- `gradle.properties`: Added `geckolib_version=5.5.1`
- `settings.gradle`: Added SpongePowered Mixin repo
- `fabric.mod.json`: Added `geckolib` dependency
- `Client.java`: Registered `ChickenWardenRenderer` for `EntityType.WARDEN`

## Key Technical Decisions
1. **GeoReplacedEntityRenderer** (not GeoEntityRenderer) — Warden is a vanilla entity that doesn't implement `GeoAnimatable`
2. **Resource paths simplified** — GeckoLib 5 auto-prepends `geckolib/models/` and appends `.geo.json`
3. **Animation JSON patched** — Bedrock `relative_to` objects converted to strings for GeckoLib format

## Remaining Issues
- **UV/texture alignment**: `chicken.png` UV layout ≠ Warden model UV → needs Blockbench re-mapping
- **Animations**: Basic idle/move playback added; attack/roar/etc. not yet wired
- **Layer effects**: Multi-layer rendering implemented via GeckoLib 5's `submitRenderTask` API; actual effect visibility depends on texture alpha channels

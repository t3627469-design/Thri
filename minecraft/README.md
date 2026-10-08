# Thri — Fabric Client (Minecraft 1.21.1, Java 21)

Testing client for your own worlds and servers you own.

## Build

```bash
cd minecraft
./gradlew build
# jar: build/libs/thri-1.0.0.jar
```

Install Fabric Loader 0.16+ for 1.21.1 and drop the jar plus Fabric API into `.minecraft/mods/`.

## Use

- `Right Shift` — open ClickGUI
- `R` — toggle KillAura
- `F` — toggle Fly
- `B` — toggle Aimbot
- Click a module name to toggle. Right-click to expand its settings. Left-click a setting row to cycle its value.
- Drag a category header to move the panel.
- Config persists to `config/thri.json`.

## Modules

Combat: KillAura, Aimbot, AutoTotem, Reach, Velocity, AntiKB, Criticals
Movement: Fly (Vanilla/Motion/Creative), Speed, NoFall, Sprint, Jesus, Freecam, Step
World: Scaffold, Nuker, FastBreak, XRay
Render: ESP, Tracers, Fullbright, Chams, HUD, NoFog
Player: ChestStealer, AutoArmor
Misc: ClickGUI

## Architecture

- Fabric mod + Mixin into `ClientPlayerEntity`, `ClientPlayNetworkHandler`, `GameRenderer`, `WorldRenderer`, `ClientPlayerInteractionManager`, `Keyboard`, `AbstractBlock$AbstractBlockState`.
- Event bus for render-world, motion-update, packet in/out.
- `ModuleManager` tick + key dispatch.
- Settings persisted via Gson.

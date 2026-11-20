# Roadmap

This document is honest about what exists today and what a next iteration would add.

## Shipped in v1.0

* Engine core with a portable renderer contract (Android + headless desktop).
* Entity/component world with a scene graph and an ordered system stack.
* AABB physics with broad phase, gravity, restitution, friction and triggers.
* Tile maps with multiple layers and tilesets.
* Sprite sheet animation, particles, behaviour scripts, audio and input abstractions.
* A complete editor: hierarchy, inspector, tile palette, tools, undo/redo, play mode.
* JSON project format, project storage on device, texture import.
* 40+ automated tests across the engine, the editor and the renderer.

## Known limitations

* **2D only.** The renderer contract, world model and editor are 2D. A 3D engine would
  need a different renderer contract (camera matrices, depth buffer, mesh pipeline) and a
  different viewport widget.
* **Physics is AABB only.** No rotation, no continuous collision detection, no joints.
  The solver is intentionally simple so it stays deterministic and testable.
* **The audio backend is a contract, not an implementation.** `AudioEngine` and `AudioBus`
  exist and are wired into the component model, but the Android build ships a null engine;
  a `SoundPool`/`AudioTrack` implementation is the obvious next step.
* **No scripting language.** Behaviours are Kotlin classes registered by id. A scripting
  runtime (or a JVM-hosted script engine) would make the editor more accessible to
  non-programmers.
* **One scene is edited at a time.** Multiple scenes exist in the project format, but there
  is no "open scene in new window" workflow.
* **No undo for play mode.** Exiting play mode restores the authored scene, but the
  simulation itself is not recorded.
* **The APK was built and unit-tested in CI-style headless builds.** There is no automated
  device/emulator run in this environment (no KVM), so UI behaviour was verified by
  compiling against the real Android SDK and by exercising the shared renderer through
  the headless desktop backend.

## Next milestones

1. **Android audio backend** – `SoundPool` implementation of `AudioEngine` plus an
   `AudioSource` mixer panel in the inspector.
2. **Scripting runtime** – expose the behaviour registry through a small embedded
   scripting language with hot reload.
3. **Rotatable bodies** – oriented bounding boxes and a SAT solver, keeping the
   deterministic step model.
4. **Prefab workflow** – author an entity as a prefab and stamp instances from the palette.
5. **Scene tabs** – open several scenes and switch between them, with play mode able to
   start a different scene.
6. **Asset pipeline** – a real content pipeline (atlas packing, import presets, texture
   compression) instead of the current procedural/demo assets.
7. **Export** – publish a project as a standalone APK or an HTML5 build, reusing the
   engine core.

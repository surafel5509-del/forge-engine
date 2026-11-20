# Architecture

## Layering

```
editor-app  ──depends──▶  editor-core  ──depends──▶  engine-core
     │                                                    ▲
     └──────────── desktop-tools (headless renderer) ─────┘
```

`engine-core` has **no Android and no Java-AWT dependencies**. Everything platform
specific lives behind two interfaces:

* `com.forge.engine.render.Renderer` – every drawing primitive the engine uses.
* `com.forge.engine.render.Texture` – a handle to pixels owned by the backend.

That is what makes the headless renderer possible: the desktop tool implements the same
contract with `java.awt`, so the automated tests and the screenshot tooling exercise the
exact code path the phone runs.

## The two models

| Model             | Lives in                | Mutated by                       |
|-------------------|-------------------------|----------------------------------|
| **Document model**| `io.ProjectData`        | `EditorDocument` through `Command`s |
| **Runtime model** | `ecs.World` / `Entity`  | the runtime systems               |

The document model is the source of truth for a saved project. The runtime model is a
transient execution of that document. `SceneBuilder` converts document → runtime,
`EditorSession.rebuild()` re-syncs after every change, and play mode starts from a fresh
rebuild so the authored scene is never polluted by simulation.

## Command pattern

```
Command.execute(document)  /  Command.undo(document)
        ▲
        │
CommandStack  ──▶  EditorSession  ──▶  EditorUiState (StateFlow)  ──▶  Compose UI
```

Every mutation is a `Command`:

| Command                | Covers                                          |
|------------------------|-------------------------------------------------|
| `AddEntityCommand`     | creating entities (optionally parented)          |
| `DeleteEntityCommand`  | deleting an entity **and its descendants**        |
| `TransformCommand`     | a single entity transform                        |
| `MultiTransformCommand`| a batch of transforms (one drag = one undo step)  |
| `RenameEntityCommand`  | renaming                                          |
| `PropertyCommand`      | any component property                            |
| `ComponentCommand`     | adding / removing / replacing a component         |
| `TileStrokeCommand`    | a whole tile stroke (records the pre-stroke values) |
| `AddLayerCommand` / `RemoveLayerCommand` | tile layers                    |
| `AddSceneCommand`      | scenes                                            |

`TileStrokeCommand` keeps an explicit snapshot of the cells it touched. The paint tool
previews the stroke live in the document, so without the snapshot the undo baseline would
already contain the new value.

## Frame flow

```
ForgeViewportView.onDraw
   ├─ session.update(dt)         transform + animation (edit) | full system stack (play)
   ├─ sceneRenderer.render(...)  tilemap → sprites (sorted by layer, z) → particles
   └─ drawEditorOverlay(...)     selection outline, transform handles, origin marker
```

The view is a regular hardware accelerated `View` (not a `SurfaceView`), so the draw calls
happen on the UI thread where the Compose tree also lives — no cross-thread access to the
document, no locking, no torn state.

## Systems

| System           | Responsibility                                          |
|------------------|---------------------------------------------------------|
| `TransformSystem`| propagates local transforms down the hierarchy          |
| `PhysicsSystem`  | syncs rigid bodies with the solver and writes results back |
| `AnimationSystem`| advances clips and drives the sprite region             |
| `ScriptSystem`   | runs behaviour scripts                                  |
| `ParticleSystem` | spawns and simulates particles                          |

## Persistence format

`project.forge.json` is pretty printed JSON:

```json
{
  "name": "Forge Demo",
  "version": 1,
  "assets": [ { "id": "player", "type": "texture", "path": "procedural://player" } ],
  "scenes": [
    {
      "name": "Level 1",
      "width": 40, "height": 22, "tileSize": 32,
      "entities": [
        { "id": 1, "name": "Player", "parentId": -1, "x": 96, "y": 512,
          "components": { "sprite": { "assetId": "player", ... } } }
      ],
      "tileLayers": [ { "name": "Terrain", "visible": true, "tiles": [ 0, 0, ... ] } ]
    }
  ]
}
```

Component payloads are produced by the codecs in `ComponentRegistry`, so the format always
matches the code that consumes it.

## Inspector schema

`InspectorSchema` declares the editable properties of every component type. The Compose
inspector is generated from that table, so adding a property means adding one line to the
schema — no UI code changes.

package com.forge.engine

import com.forge.engine.ecs.ComponentRegistry
import com.forge.engine.ecs.Entity
import com.forge.engine.io.DemoProject
import com.forge.engine.io.ProjectCodec
import com.forge.engine.scene.SceneBuilder
import com.forge.engine.io.DemoAssets
import com.forge.engine.scripting.registerBuiltinBehaviors
import com.forge.engine.systems.EngineSystems
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectCodecTest {

    @Test
    fun projectRoundTripsThroughJson() {
        val project = DemoProject.create()
        val json = ProjectCodec.encode(project)
        val restored = ProjectCodec.decode(json)

        assertEquals(project.name, restored.name)
        assertEquals(project.scenes.size, restored.scenes.size)
        assertEquals(project.assets.size, restored.assets.size)
        assertEquals(
            project.scenes.first().entities.size,
            restored.scenes.first().entities.size
        )
        assertEquals(
            project.scenes.first().tileLayers.first().tiles,
            restored.scenes.first().tileLayers.first().tiles
        )
    }

    @Test
    fun componentPayloadsSurviveRoundTrip() {
        val project = DemoProject.create()
        val restored = ProjectCodec.decode(ProjectCodec.encode(project))
        val original = project.scenes.first().entities.first { it.name == "Player" }
        val copy = restored.scenes.first().entities.first { it.name == "Player" }

        assertEquals(original.components.keys, copy.components.keys)
        assertEquals(
            original.components["rigidbody"],
            copy.components["rigidbody"]
        )
    }

    @Test
    fun registryDecodesKnownComponents() {
        val project = DemoProject.create()
        val entity = project.scenes.first().entities.first { it.name == "Player" }
        val decoded = ProjectCodec.decodeComponents(entity)
        assertTrue(decoded.containsKey("sprite"))
        assertTrue(decoded.containsKey("rigidbody"))
        assertEquals("player", (decoded["sprite"] as com.forge.engine.components.SpriteComponent).assetId)
    }

    @Test
    fun unknownComponentsAreIgnoredInsteadOfCrashing() {
        val project = DemoProject.create()
        val entity = project.scenes.first().entities.first()
        entity.components["mystery"] = kotlinx.serialization.json.buildJsonObject { }
        val decoded = ProjectCodec.decodeComponents(entity)
        assertTrue(decoded.isNotEmpty())
    }
}

class WorldTest {

    @Test
    fun hierarchyPropagatesWorldTransforms() {
        val world = EngineSystems.standardWorld()
        val parent = world.createEntity("Parent")
        parent.position.set(100f, 50f)
        val child = world.createEntity("Child")
        child.position.set(10f, 5f)
        parent.addChild(child)

        world.system("transform")?.update(world, 0f)

        assertEquals(110f, child.worldPosition.x, 1e-3f)
        assertEquals(55f, child.worldPosition.y, 1e-3f)
    }

    @Test
    fun behaviorScriptsRunInPlayMode() {
        registerBuiltinBehaviors()
        val world = EngineSystems.standardWorld()
        val entity = world.createEntity("Spinner")
        entity.addComponent(
            com.forge.engine.components.ScriptComponent().apply {
                scriptId = "spin"
                properties["speed"] = "90"
            }
        )
        world.update(1f)
        assertTrue(entity.rotation > 0f, "spin behavior should rotate the entity")
    }

    @Test
    fun physicsMovesRigidBodies() {
        val world = EngineSystems.standardWorld()
        val entity = world.createEntity("Ball")
        entity.addComponent(
            com.forge.engine.components.RigidBodyComponent().apply {
                width = 20f; height = 20f
            }
        )
        world.update(0.1f)
        assertTrue(entity.position.y > 0f, "gravity should move the body down")
    }

    @Test
    fun sceneBuilderRebuildsHierarchyFromData() {
        val project = DemoProject.create()
        val assets = DemoAssets.providerFor(project)
        val world = SceneBuilder.buildWorld(project.scenes.first(), assets)

        assertEquals(project.scenes.first().entities.size, world.entities.size)
        assertTrue(world.findEntity(1)?.name == "Player")
        assertNotNull(world.findEntity(1)?.getComponent("sprite"))
    }

    @Test
    fun removingEntityRemovesDescendants() {
        val project = DemoProject.create()
        val scene = project.scenes.first()
        val parent = scene.entities.first()
        val child = scene.entities.last()
        child.parentId = parent.id

        val removed = SceneBuilder.removeEntities(scene, setOf(parent.id))
        assertTrue(removed.contains(parent.id))
        assertTrue(removed.contains(child.id))
        assertTrue(scene.entities.none { it.id == parent.id })
        assertTrue(scene.entities.none { it.id == child.id })
    }
}

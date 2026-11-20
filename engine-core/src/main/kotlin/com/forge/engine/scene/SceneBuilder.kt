package com.forge.engine.scene

import com.forge.engine.ecs.Entity
import com.forge.engine.ecs.World
import com.forge.engine.io.EntityData
import com.forge.engine.io.ProjectCodec
import com.forge.engine.io.SceneData
import com.forge.engine.render.AssetProvider
import com.forge.engine.systems.EngineSystems

/**
 * Turns persisted scene data into a live runtime world: entities, hierarchy and
 * components are rebuilt and the standard system stack is installed.
 */
object SceneBuilder {

    fun buildWorld(scene: SceneData, assets: AssetProvider): World {
        val world = EngineSystems.standardWorld()

        val byId = LinkedHashMap<Int, Entity>()
        for (data in scene.entities) {
            val entity = Entity(data.id, data.name)
            entity.position.set(data.x, data.y)
            entity.rotation = data.rotation
            entity.scale.set(data.scaleX, data.scaleY)
            entity.visible = data.visible
            entity.tag = data.tag
            for ((typeId, component) in ProjectCodec.decodeComponents(data)) {
                entity.addComponent(component)
            }
            world.addEntity(entity)
            byId[data.id] = entity
        }

        for (data in scene.entities) {
            if (data.parentId < 0) continue
            val parent = byId[data.parentId] ?: continue
            parent.addChild(byId[data.id] ?: continue)
        }

        // World transform pass so the first frame already has correct positions.
        world.system("transform")?.update(world, 0f)
        return world
    }

    /** Copies runtime entity state back into the editable data model. */
    fun syncBack(world: World, scene: SceneData) {
        val byId = scene.entities.associateBy { it.id }
        for (entity in world.entities) {
            val data = byId[entity.id] ?: continue
            data.x = entity.position.x
            data.y = entity.position.y
            data.rotation = entity.rotation
            data.scaleX = entity.scale.x
            data.scaleY = entity.scale.y
            data.visible = entity.visible
            data.tag = entity.tag
            data.name = entity.name
        }
    }

    /** Removes entities (and descendants) from the scene data. */
    fun removeEntities(scene: SceneData, rootIds: Set<Int>): Set<Int> {
        val removed = LinkedHashSet<Int>()
        fun collect(id: Int) {
            val data = scene.entities.firstOrNull { it.id == id } ?: return
            if (!removed.add(id)) return
            data.components.keys.forEach { }
            scene.entities.filter { it.parentId == id }.forEach { collect(it.id) }
        }
        rootIds.forEach { collect(it) }
        scene.entities.removeAll { it.id in removed }
        return removed
    }
}

package com.forge.editor.core

import com.forge.engine.input.MouseButton
import com.forge.engine.io.DemoProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorToolsTest {

    private fun context(session: EditorSession, wx: Float, wy: Float): ToolContext =
        ToolContext(session, PointerInfo(wx, wy, wx, wy, MouseButton.LEFT))

    @Test
    fun selectToolPicksEntityAndDragMovesIt() {
        val session = EditorSession(DemoProject.create())
        val player = session.document.entities().first { it.name == "Player" }
        val startX = player.x

        val tool = SelectTool()
        tool.onPointerDown(context(session, player.x + 4f, player.y + 4f))
        assertTrue(session.selection.contains(player.id))

        tool.onPointerMove(context(session, player.x + 40f, player.y + 4f))
        val moved = session.document.entity(player.id)!!
        assertTrue(moved.x > startX, "entity should follow the pointer")

        tool.onPointerUp(context(session, player.x + 40f, player.y + 4f))
        assertTrue(session.commands.canUndo)
        session.commands.undo()
        assertEquals(startX, session.document.entity(player.id)!!.x, 1e-3f)
    }

    @Test
    fun selectToolClearsSelectionWhenClickingEmptySpace() {
        val session = EditorSession(DemoProject.create())
        session.select(session.document.entities().first().id)
        val tool = SelectTool()
        tool.onPointerDown(context(session, -500f, -500f))
        assertTrue(session.selection.isEmpty())
    }

    @Test
    fun tilePaintToolPaintsAndUndoRevertsStroke() {
        val session = EditorSession(DemoProject.create())
        session.activeLayerIndex = 0
        session.currentTileGid = 5
        val tool = TilePaintTool(false)

        tool.onPointerDown(context(session, 32f * 2f + 4f, 32f * 3f + 4f))
        tool.onPointerMove(context(session, 32f * 3f + 4f, 32f * 3f + 4f))
        tool.onPointerUp(context(session, 32f * 3f + 4f, 32f * 3f + 4f))

        val layer = session.document.scene.tileLayers.first()
        assertEquals(5, layer.tiles[3 * session.document.scene.width + 2])

        session.commands.undo()
        assertEquals(0, layer.tiles[3 * session.document.scene.width + 2])
    }

    @Test
    fun spawnToolCreatesEntityAtClickPosition() {
        val session = EditorSession(DemoProject.create())
        val before = session.document.entities().size
        val tool = SpawnTool()
        tool.onPointerDown(context(session, 320f, 160f))
        assertEquals(before + 1, session.document.entities().size)
        val created = session.document.entities().last()
        assertEquals(320f, created.x)
        assertEquals(160f, created.y)
    }

    @Test
    fun rotateToolRotatesSelectedEntity() {
        val session = EditorSession(DemoProject.create())
        val player = session.document.entities().first { it.name == "Player" }
        session.select(player.id)
        val tool = RotateTool()
        tool.onPointerDown(context(session, player.x + 100f, player.y))
        tool.onPointerMove(context(session, player.x, player.y + 100f))
        tool.onPointerUp(context(session, player.x, player.y + 100f))
        assertTrue(player.rotation > 0.5f, "entity should rotate, was ${player.rotation}")
    }

    @Test
    fun hierarchyPathIsBuiltFromParents() {
        val session = EditorSession(DemoProject.create())
        val player = session.document.entities().first { it.name == "Player" }
        assertEquals("Player", session.document.hierarchyPath(player.id))
    }
}

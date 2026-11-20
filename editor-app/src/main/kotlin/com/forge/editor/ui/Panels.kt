package com.forge.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.editor.EditorUiState
import com.forge.editor.PropertyUi
import com.forge.editor.EditorViewModel
import com.forge.editor.core.InspectorSchema
import com.forge.editor.core.PropertyType
import kotlinx.serialization.json.JsonPrimitive

/** Left hand panel: scenes, hierarchy tree and assets. */
@Composable
fun ScenesPanel(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        PanelHeader("Scenes", trailing = "${state.scenes.size}")
        state.scenes.forEachIndexed { index, name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setActiveScene(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == state.activeScene) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Spacer(Modifier.width(14.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    name,
                    fontSize = 12.sp,
                    fontWeight = if (index == state.activeScene) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == state.activeScene) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            ToolButton("+ Scene", { vm.addScene() })
        }
    }
}

/** Hierarchy tree of the active scene. */
@Composable
fun HierarchyPanel(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        PanelHeader("Hierarchy", trailing = "${state.hierarchy.size}")
        LazyColumn(
            modifier = Modifier.height(220.dp).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
        ) {
            items(state.hierarchy, key = { it.id }) { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectEntity(node.id, false) }
                        .padding(start = (8 + node.depth * 12).dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        node.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        fontWeight = if (node.selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (node.selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { vm.toggleVisibility(node.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (node.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Project assets: textures and tilesets available to the scene. */
@Composable
fun AssetsPanel(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        PanelHeader("Assets", trailing = "${state.assets.size}")
        state.assets.forEach { asset ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
                Spacer(Modifier.width(8.dp))
                Text(asset.id, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(asset.type, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Right hand panel: the inspector for the current selection. */
@Composable
fun InspectorPanel(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (state.selectedId < 0) {
            InspectorSection("Inspector") {
                Text(
                    "Nothing selected. Pick an entity in the hierarchy or in the viewport.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            return@Column
        }

        InspectorSection("Entity") {
            var name by remember(state.selectedId, state.selectedName) { mutableStateOf(state.selectedName) }
            com.forge.editor.ui.StringField(
                label = "Name",
                value = name,
                resetKey = state.selectedId,
                onValueChange = {
                    name = it
                    vm.renameEntity(state.selectedId, it)
                }
            )
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                ToolButton("Delete", { vm.deleteSelected() }, enabled = true)
            }
        }

        InspectorSection("Transform") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberField("X", state.transform.x, resetKey = state.selectedId) { vm.setTransform(state.selectedId, state.transform.copy(x = it)) }
                NumberField("Y", state.transform.y, resetKey = state.selectedId) { vm.setTransform(state.selectedId, state.transform.copy(y = it)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberField("Rotation", state.transform.rotation, step = 1f, resetKey = state.selectedId) {
                    vm.setTransform(state.selectedId, state.transform.copy(rotation = it))
                }
                NumberField("Scale X", state.transform.scaleX, resetKey = state.selectedId) {
                    vm.setTransform(state.selectedId, state.transform.copy(scaleX = it))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                NumberField("Scale Y", state.transform.scaleY, resetKey = state.selectedId) {
                    vm.setTransform(state.selectedId, state.transform.copy(scaleY = it))
                }
            }
        }

        state.components.forEach { component ->
            InspectorSection(component.title) {
                component.properties.forEach { property ->
                    when (property.type) {
                        PropertyType.FLOAT -> FloatSlider(
                            property.label, property.floatValue, property.min, property.max
                        ) { vm.setProperty(state.selectedId, component.typeId, property.key, JsonPrimitive(it)) }

                        PropertyType.INT -> Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(property.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            NumberField(
                                property.label,
                                property.intValue.toFloat(),
                                resetKey = state.selectedId
                            ) {
                                vm.setProperty(state.selectedId, component.typeId, property.key, JsonPrimitive(it.toInt()))
                            }
                        }

                        PropertyType.BOOL -> BooleanField(property.label, property.boolValue) {
                            vm.setProperty(state.selectedId, component.typeId, property.key, JsonPrimitive(it))
                        }

                        PropertyType.STRING -> StringField(
                            property.label, property.stringValue, resetKey = state.selectedId
                        ) { vm.setProperty(state.selectedId, component.typeId, property.key, JsonPrimitive(it)) }

                        PropertyType.ASSET -> AssetPickerField(
                            state, component.typeId, property, vm
                        )

                        PropertyType.COLOR -> EngineColorField(
                            property.label,
                            property.color
                        ) { vm.setColorProperty(state.selectedId, component.typeId, property.key, it) }
                    }
                }
            }
        }

        AddComponentRow(state, vm)
    }
}

/** Dropdown that lets the user pick a texture asset. */
@Composable
private fun AssetPickerField(
    state: EditorUiState,
    componentTypeId: String,
    property: PropertyUi,
    vm: EditorViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                property.label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                property.stringValue.ifEmpty { "none" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.assets.filter { it.type == "texture" || it.type == "tileset" }.forEach { asset ->
                DropdownMenuItem(
                    text = { Text(asset.id, fontSize = 12.sp) },
                    onClick = {
                        vm.setProperty(state.selectedId, componentTypeId, property.key, JsonPrimitive(asset.id))
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Row that adds a missing component to the selected entity. */
@Composable
private fun AddComponentRow(state: EditorUiState, vm: EditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    InspectorSection("Add Component") {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Box {
                ToolButton("+ Component", { expanded = true })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    InspectorSchema.knownTypeIds().forEach { typeId ->
                        DropdownMenuItem(
                            text = { Text(InspectorSchema.titleOf(typeId), fontSize = 12.sp) },
                            onClick = {
                                vm.addComponent(state.selectedId, typeId)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Tile palette shown while a tile tool is active. */
@Composable
fun TilePalettePanel(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        PanelHeader("Tile Palette", trailing = "layer ${state.activeLayer}")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.tileIds.forEach { gid ->
                val selected = gid == state.currentTile
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { vm.selectTile(gid) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$gid",
                        fontSize = 11.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PanelHeader("Layers")
        state.layers.forEachIndexed { index, layer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.selectLayer(index) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    layer.name,
                    fontSize = 12.sp,
                    fontWeight = if (index == state.activeLayer) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == state.activeLayer) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.toggleLayerVisibility(index) }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle layer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolButton("+ Layer", { vm.addLayer() })
            ToolButton("Remove", { vm.removeLayer() })
        }
    }
}

/** Floating vertical toolbar with the editor tools. */
@Composable
fun ToolStrip(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.tools.forEach { tool ->
            val active = tool.id == state.activeTool
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .clickable { vm.selectTool(com.forge.editor.core.EditorTools.byId(tool.id)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tool.title.take(2),
                    fontSize = 10.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

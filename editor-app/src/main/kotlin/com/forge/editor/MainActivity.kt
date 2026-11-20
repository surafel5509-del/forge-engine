package com.forge.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.forge.editor.ui.EditorScreen
import com.forge.editor.ui.ForgeEditorTheme

/**
 * Entry point of the Forge Engine editor: a full screen, dark themed 2D game
 * engine and level editor.
 */
class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ForgeEditorTheme {
                EditorScreen(editorViewModel)
            }
        }
    }
}

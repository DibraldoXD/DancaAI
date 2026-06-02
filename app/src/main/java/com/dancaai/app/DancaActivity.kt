package com.dancaai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dancaai.app.navigation.DancaApp

/**
 * Activity de entrada do app (UI em Jetpack Compose).
 * A MainActivity (câmera/MediaPipe) será integrada à tela de Treino na Fase 4.
 */
class DancaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DancaApp()
        }
    }
}

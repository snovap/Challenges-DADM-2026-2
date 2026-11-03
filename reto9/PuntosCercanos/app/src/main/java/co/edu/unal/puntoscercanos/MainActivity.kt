package co.edu.unal.puntoscercanos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.edu.unal.puntoscercanos.map.OsmSetup
import co.edu.unal.puntoscercanos.ui.PuntosApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OsmSetup.init(applicationContext)
        setContent { PuntosApp() }
    }
}
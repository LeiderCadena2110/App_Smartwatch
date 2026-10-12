package co.edu.ucc.hidrasmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HidraApp() }
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        super.onPause()
        visible = false
    }

    companion object {
        /** Si la app está en pantalla, la alerta se muestra dentro de ella y no aparte. */
        @Volatile
        var visible = false
    }
}

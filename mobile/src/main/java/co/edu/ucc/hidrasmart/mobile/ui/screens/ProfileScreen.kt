package co.edu.ucc.hidrasmart.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.SweatTest
import co.edu.ucc.hidrasmart.format.toFixed
import co.edu.ucc.hidrasmart.mobile.BuildConfig
import co.edu.ucc.hidrasmart.mobile.MobileViewModel
import co.edu.ucc.hidrasmart.mobile.ui.components.NumberField
import co.edu.ucc.hidrasmart.mobile.ui.components.SectionTitle
import co.edu.ucc.hidrasmart.mobile.ui.components.toDecimalOrNull
import co.edu.ucc.hidrasmart.mobile.ui.theme.BrandColors

@Composable
fun ProfileScreen(vm: MobileViewModel) {
    val p = vm.profile
    var weight by rememberSaveable { mutableStateOf(p.weightKg.toFixed(1)) }
    var age by rememberSaveable { mutableStateOf(p.age.toString()) }
    var goal by rememberSaveable { mutableStateOf(p.dailyGoalMl.toString()) }
    var savedMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Perfil")
        NumberField("Peso", weight, { weight = it.take(5) }, decimal = true, suffix = "kg")
        NumberField("Edad", age, { age = it.take(3) }, suffix = "años")
        NumberField("Meta diaria", goal, { goal = it.take(5) }, suffix = "ml")
        Button(
            onClick = {
                val w = weight.toDecimalOrNull()
                val a = age.toIntOrNull()
                val g = goal.toIntOrNull()
                savedMessage = if (w != null && w in 30.0..200.0 && a != null && a in 10..90 && g != null && g in 500..6000) {
                    vm.saveProfile(p.copy(weightKg = w, age = a, dailyGoalMl = g))
                    "Perfil guardado"
                } else {
                    "Revisa: peso 30–200 kg, edad 10–90, meta 500–6000 ml"
                }
            },
        ) { Text("Guardar perfil") }
        savedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        SweatTestSection(vm)
        AiSettingsSection(vm)

        Text(
            "HidraSmart ${BuildConfig.VERSION_NAME} · Orientación general, no reemplaza a un profesional de la salud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Prueba de sudoración (NATA 2017): pesarse antes y después de 1 hora de ejercicio. */
@Composable
private fun SweatTestSection(vm: MobileViewModel) {
    var before by rememberSaveable { mutableStateOf(vm.profile.weightKg.toFixed(1)) }
    var after by rememberSaveable { mutableStateOf((vm.profile.weightKg - 0.5).toFixed(1)) }
    var fluid by rememberSaveable { mutableStateOf("0") }
    var urine by rememberSaveable { mutableStateOf("0") }
    var minutes by rememberSaveable { mutableStateOf("60") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    SectionTitle("Prueba de sudoración")
    Text(
        vm.profile.sweatRateLh?.let { "Tu tasa actual: ${it.toFixed(2)} L/h" } ?: "Aún no la has hecho.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Peso antes", before, { before = it.take(5) }, Modifier.weight(1f), decimal = true, suffix = "kg")
        NumberField("Peso después", after, { after = it.take(5) }, Modifier.weight(1f), decimal = true, suffix = "kg")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Líquido", fluid, { fluid = it.take(4) }, Modifier.weight(1f), suffix = "ml")
        NumberField("Orina", urine, { urine = it.take(4) }, Modifier.weight(1f), suffix = "ml")
    }
    NumberField("Duración", minutes, { minutes = it.take(3) }, suffix = "min")
    OutlinedButton(
        onClick = {
            val i = before.toDecimalOrNull()
            val f = after.toDecimalOrNull()
            val fl = fluid.toIntOrNull()
            val u = urine.toIntOrNull()
            val m = minutes.toIntOrNull()
            result = if (i == null || f == null || fl == null || u == null || m == null) {
                "Completa todos los campos con números."
            } else {
                val test = SweatTest(i, f, fl, u, m)
                vm.saveSweatTest(test).fold(
                    onSuccess = { rate ->
                        val loss = HydrationCalculator.weightLossPercent(test)
                        "Tasa: ${rate.toFixed(2)} L/h. Perdiste ${loss.toFixed(1)} % del peso" +
                            if (loss >= 2.0) " (NATA recomienda menos del 2 %)." else "."
                    },
                    onFailure = { it.message ?: "Revisa los datos" },
                )
            }
        },
    ) { Text("Calcular y guardar") }
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BrandColors.Water) }
}

/** Clave de Gemini guardada solo en este teléfono. */
@Composable
private fun AiSettingsSection(vm: MobileViewModel) {
    var key by rememberSaveable { mutableStateOf(vm.userGeminiKey) }
    SectionTitle("Coach de IA")
    Text(
        if (vm.cloudAiEnabled) "Gemini activo. Si no hay internet responde la IA local."
        else "Sin clave: el coach usa la IA local, que funciona sin internet.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it.trim() },
        label = { Text("Clave de Gemini (Google AI Studio)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.saveGeminiKey(key) }) { Text("Guardar clave") }
        OutlinedButton(onClick = {
            key = ""
            vm.saveGeminiKey("")
        }) { Text("Quitar") }
    }
    Text(
        "La clave se guarda solo en este teléfono. Consíguela gratis en aistudio.google.com.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

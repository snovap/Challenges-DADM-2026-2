package co.edu.unal.directorio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.directorio.model.Classification
import co.edu.unal.directorio.model.Company
import co.edu.unal.directorio.model.CompanyForm
import co.edu.unal.directorio.model.FormErrors

@Composable
fun CompanyFormScreen(
    initial: Company?,
    dark: Boolean,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onTogglePalette: () -> Unit,
    onSave: (CompanyForm) -> Unit
) {
    val c = LocalAppColors.current
    val editing = initial != null

    // rememberSaveable: lo escrito sobrevive a la rotación de pantalla.
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var website by rememberSaveable { mutableStateOf(initial?.website.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(initial?.phone.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initial?.email.orEmpty()) }
    var services by rememberSaveable { mutableStateOf(initial?.productsAndServices.orEmpty()) }
    var mask by rememberSaveable { mutableStateOf(initial?.let { Classification.toMask(it.classifications) } ?: 0) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val form = CompanyForm(name, website, phone, email, services, Classification.fromMask(mask))
    val errors = if (submitted) form.validate() else FormErrors()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader(
            if (editing) "Editar empresa" else "Nueva empresa", null, onBack, dark, onToggleTheme, onTogglePalette
        )

        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(
                if (editing) "ACTUALIZA EL PERFIL" else "AMPLÍA EL DIRECTORIO",
                color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp
            )
            Text(
                if (editing) "Información de la empresa" else "Agrega una empresa",
                color = c.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold
            )
            Text("Nombre y clasificación son obligatorios.", color = c.textMuted, fontSize = 12.sp)
        }

        if (submitted && errors.hasErrors) ErrorBanner()

        AppTextField(
            "Nombre *", name, { name = it },
            placeholder = "Ej. NovaSoft Consultores", error = errors.name,
            capitalization = KeyboardCapitalization.Words
        )
        AppTextField(
            "URL", website, { website = it },
            placeholder = "empresa.com", error = errors.website, keyboardType = KeyboardType.Uri
        )
        AppTextField(
            "Teléfono", phone, { phone = it },
            placeholder = "+57 601 000 0000", error = errors.phone, keyboardType = KeyboardType.Phone
        )
        AppTextField(
            "Email", email, { email = it },
            placeholder = "hola@empresa.com", error = errors.email, keyboardType = KeyboardType.Email
        )
        AppTextField(
            "Productos y servicios", services, { services = it },
            placeholder = "Describe brevemente su oferta...",
            capitalization = KeyboardCapitalization.Sentences, singleLine = false, minLines = 4
        )

        Column(Modifier.fillMaxWidth().surfaceCard().padding(18.dp)) {
            Text("Clasificación", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Selecciona una o varias opciones", color = c.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Classification.entries.forEach { option ->
                val checked = (mask and option.bit) != 0
                CheckRow(option.label, checked) { mask = mask xor option.bit }
            }
            errors.classification?.let {
                Text(it, color = c.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Cancelar", onBack, Modifier.weight(1f), kind = ButtonKind.SECONDARY)
            AppButton(
                "Guardar",
                {
                    submitted = true
                    if (!form.validate().hasErrors) onSave(form)
                },
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ErrorBanner() {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.dangerSoft)
            .border(1.dp, c.danger.copy(alpha = 0.35f), shape).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(25.dp).border(1.dp, c.danger, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) { Text("!", color = c.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Column {
            Text("Falta información", color = c.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Revisa los campos marcados para continuar.", color = c.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(21.dp).clip(shape)
                .background(if (checked) c.accent else Color.Transparent)
                .border(1.dp, if (checked) c.accent else c.outlineStrong, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(11.dp))
        Text(label, color = c.text, fontSize = 14.sp)
    }
}
package co.edu.unal.directorio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.edu.unal.directorio.model.Company

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanyDetailScreen(
    company: Company?,
    dark: Boolean,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onTogglePalette: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onContactError: (String) -> Unit
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader("Empresa", null, onBack, dark, onToggleTheme, onTogglePalette)

        if (company == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
        } else {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
                InitialsMark(company.name, 82.dp, 24.dp, 20.sp)
                Spacer(Modifier.height(18.dp))
                Text(
                    "PERFIL DE EMPRESA", color = c.accent, fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp
                )
                Text(
                    company.name, color = c.text, fontSize = 30.sp, lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    company.classifications.sortedBy { it.bit }.forEach { CategoryChip(it.label, selected = true) }
                }
            }

            Column(Modifier.fillMaxWidth().surfaceCard().padding(horizontal = 18.dp, vertical = 8.dp)) {
                DetailRow(Icons.Default.Language, "Sitio web", company.website) {
                    if (!context.openWebsite(company.website)) onContactError("No hay un navegador disponible.")
                }
                HorizontalDivider(color = c.outline)
                DetailRow(Icons.Default.Phone, "Teléfono", company.phone) {
                    if (!context.openDialer(company.phone)) onContactError("No hay una app para llamar.")
                }
                HorizontalDivider(color = c.outline)
                DetailRow(Icons.Default.Email, "Email", company.email) {
                    if (!context.openEmail(company.email)) onContactError("No hay una app de correo.")
                }
            }

            Row(
                Modifier.fillMaxWidth().surfaceCard().padding(horizontal = 18.dp, vertical = 19.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AccentIconBox(Icons.Default.Business, iconSize = 18.dp)
                Column {
                    Text("Productos y servicios", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        company.productsAndServices.ifBlank { "Sin descripción" },
                        color = c.textMuted, fontSize = 13.sp, lineHeight = 20.sp
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("Editar", onEdit, Modifier.weight(1f), Icons.Default.Edit, ButtonKind.SECONDARY)
                AppButton("Eliminar", { showDialog = true }, Modifier.weight(1f), Icons.Default.Delete, ButtonKind.DANGER)
            }
        }
    }

    if (showDialog && company != null) {
        DeleteCompanyDialog(
            name = company.name,
            onCancel = { showDialog = false },
            onConfirm = { showDialog = false; onDelete() }
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = value.isNotBlank(), onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentIconBox(icon)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = c.textMuted, fontSize = 11.sp)
            Text(
                value.ifBlank { "No registrado" },
                color = if (value.isBlank()) c.textMuted else c.text,
                fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Confirmación obligatoria antes de borrar (requisito del reto). */
@Composable
private fun DeleteCompanyDialog(name: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.surfaceStrong)
                .border(1.dp, c.outlineStrong, shape).padding(25.dp)
        ) {
            val iconShape = RoundedCornerShape(16.dp)
            Box(
                Modifier.size(48.dp).clip(iconShape).background(c.dangerSoft),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Delete, null, tint = c.danger, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("¿Eliminar a $name?", color = c.text, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Esta acción no se puede deshacer. La empresa dejará de aparecer en el directorio.",
                color = c.textMuted, fontSize = 13.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AppButton("Cancelar", onCancel, Modifier.weight(1f), kind = ButtonKind.SECONDARY)
                AppButton("Eliminar", onConfirm, Modifier.weight(1f), kind = ButtonKind.DANGER_FILLED)
            }
        }
    }
}
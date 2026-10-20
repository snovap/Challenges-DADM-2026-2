package co.edu.unal.directorio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.directorio.model.Classification
import co.edu.unal.directorio.model.Company

@Composable
fun CompanyListScreen(
    state: CompaniesUiState,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onTogglePalette: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Classification?) -> Unit,
    onOpen: (Company) -> Unit,
    onAdd: () -> Unit,
    onContactError: (String) -> Unit
) {
    val c = LocalAppColors.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AppHeader("Directorio Tech", "Empresas de software", null, dark, onToggleTheme, onTogglePalette) }
            item { SearchField(state.query, onQueryChange) }
            item { FilterRow(state.classificationFilter, onFilterChange) }
            item { ResultsHeading(state.companies.size) }
            if (state.companies.isEmpty()) {
                if (!state.loading) item { EmptyState(filtered = state.isFiltering, onAdd = onAdd) }
            } else {
                items(state.companies, key = { it.id }) { company ->
                    CompanyCard(company, onOpen = { onOpen(company) }, onContactError = onContactError)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            containerColor = c.accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Nueva empresa", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterRow(selected: Classification?, onSelect: (Classification?) -> Unit) {
    val options: List<Pair<Classification?, String>> =
        listOf<Pair<Classification?, String>>(null to "Todas") + Classification.entries.map { it to it.label }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) -> FilterChip(label, active = selected == value) { onSelect(value) } }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(38.dp).clip(shape)
            .background(if (active) c.accent else c.surfaceSoft)
            .border(1.dp, if (active) c.accent else c.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (active) Color.White else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultsHeading(count: Int) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "$count ${if (count == 1) "empresa" else "empresas"}",
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        Text("Orden A–Z", color = c.textMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyCard(company: Company, onOpen: () -> Unit, onContactError: (String) -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val summary = company.productsAndServices.split(",").first().trim().ifEmpty { "Sin descripción" }

    Column(Modifier.fillMaxWidth().surfaceCard().clickable(onClick = onOpen).padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 17.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsMark(company.name, 46.dp, 16.dp, 13.sp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    company.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    company.classifications.sortedBy { it.bit }.forEach { CategoryChip(it.label) }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = c.textMuted)
        }
        HorizontalDivider(color = c.outline)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summary, color = c.textMuted, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIconButton(
                    Icons.Default.Phone, "Llamar a ${company.name}",
                    { if (!context.openDialer(company.phone)) onContactError("No hay una app para llamar.") },
                    size = 32.dp, radius = 11.dp, iconSize = 18.dp, enabled = company.phone.isNotBlank()
                )
                AppIconButton(
                    Icons.Default.Email, "Enviar email a ${company.name}",
                    { if (!context.openEmail(company.email)) onContactError("No hay una app de correo.") },
                    size = 32.dp, radius = 11.dp, iconSize = 18.dp, enabled = company.email.isNotBlank()
                )
                AppIconButton(
                    Icons.Default.Language, "Visitar sitio de ${company.name}",
                    { if (!context.openWebsite(company.website)) onContactError("No hay un navegador disponible.") },
                    size = 32.dp, radius = 11.dp, iconSize = 18.dp, enabled = company.website.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onAdd: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(width = 180.dp, height = 150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = Stroke(width = 1.dp.toPx())
                drawCircle(color = c.outline, radius = 63.dp.toPx(), style = stroke)
                rotate(55f) {
                    drawOval(
                        color = c.outline,
                        topLeft = Offset(center.x - 35.dp.toPx(), 0f),
                        size = Size(70.dp.toPx(), size.height),
                        style = stroke
                    )
                }
            }
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier.size(90.dp).clip(shape)
                    .background(Brush.linearGradient(listOf(c.accentSoft, c.surface)))
                    .border(1.dp, c.outlineStrong, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (filtered) Icons.Default.Search else Icons.Default.Business, null,
                    tint = c.accent, modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "Sin coincidencias" else "No hay empresas",
            color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (filtered) "Prueba con otro nombre o cambia el filtro." else "¡Agrega la primera!",
            color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        if (!filtered) {
            Spacer(Modifier.height(20.dp))
            AppButton("Nueva empresa", onAdd, icon = Icons.Default.Add)
        }
    }
}
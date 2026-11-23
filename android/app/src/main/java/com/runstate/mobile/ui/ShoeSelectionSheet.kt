package com.runstate.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Formatted presentation only; persistence and mileage belong to the journey owner. */
internal data class ShoePairDisplay(
    val id: String,
    val brand: String,
    val model: String,
    val nickname: String? = null,
    val mileageLabel: String,
    val archived: Boolean = false
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: model
    val fullName: String get() = listOfNotNull(brand, model, nickname?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

private data class ShoeCatalogChoice(val brand: String, val model: String)

private val ShoeCatalog = listOf(
    ShoeCatalogChoice("ASICS", "Novablast 6"),
    ShoeCatalogChoice("Nike", "Pegasus 42"),
    ShoeCatalogChoice("HOKA", "Clifton 11"),
    ShoeCatalogChoice("Brooks", "Ghost 18"),
    ShoeCatalogChoice("Saucony", "Ride 19"),
    ShoeCatalogChoice("HOKA", "Speedgoat 7")
)
private val ShoeInk = Color(0xFF293326)
private val ShoeMuted = Color(0xFF677262)
private val ShoeSurface = Color(0xFFF9FAF5)
private val ShoeSage = Color(0xFFE9EEDF)

/** The owner closes this sheet only after an add or selection has actually saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoeSelectionSheet(
    shoes: List<ShoePairDisplay>,
    selectedShoeId: String?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onAdd: (String, String, String?) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onUndoArchive: () -> Unit,
    undoLabel: String? = null,
    error: String? = null,
    loading: Boolean = false,
    onRetryLoad: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    fun matches(value: String) = search.isEmpty() || value.contains(search, ignoreCase = true)
    val active = shoes.filter { !it.archived && matches(it.fullName) }
    val archived = shoes.filter { it.archived && matches(it.fullName) }
    val catalog = ShoeCatalog.filter { choice ->
        matches("${choice.brand} ${choice.model}") && shoes.none {
            it.brand.equals(choice.brand, true) && it.model.equals(choice.model, true)
        }
    }
    val controlsEnabled = enabled && !loading
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ShoeSurface,
        contentColor = ShoeInk,
        modifier = modifier.testTag("shoeSheet")
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Add Shoes", fontFamily = CompletionNumberFont, fontWeight = FontWeight.Medium,
                    fontSize = 34.sp, color = ShoeInk, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Close", color = ShoeMuted)
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search brand or model") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), colors = shoeFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("shoeSearch")
            )
            if (loading) Text("Loading your shoes…", color = ShoeMuted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            if (error != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = ShoeInk, fontSize = 13.sp,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                    if (onRetryLoad != null) TextButton(onClick = onRetryLoad, enabled = !loading) {
                        Text("Retry", color = ShoeInk)
                    }
                }
            }
            if (undoLabel != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(ShoeSage).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(undoLabel, color = ShoeInk, fontSize = 13.sp, modifier = Modifier.weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = onUndoArchive, enabled = controlsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("shoeUndo")) {
                        Text("Undo", color = ShoeInk, fontWeight = FontWeight.Bold)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("shoeList"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    ShoeSectionLabel("YOUR SHOES")
                    if (active.isEmpty() && !loading) Text(
                        if (search.isEmpty()) "Add your first pair below." else "No saved shoes match your search.",
                        color = ShoeMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                }
                items(active, key = { it.id }) { shoe ->
                    SavedShoeRow(shoe, shoe.id == selectedShoeId, controlsEnabled,
                        onSelect = { onSelect(shoe.id) }, onArchive = { onArchive(shoe.id) })
                }
                item {
                    TextButton(onClick = { onSelect(null) }, enabled = controlsEnabled,
                        modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)
                            .testTag("shoeNoSelection")) {
                        Text("Run without tracking shoes", color = ShoeMuted)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = ShoeSage)
                    ShoeSectionLabel("BROWSE SHOES")
                    Text("Mileage tracking begins with your next saved GPS run.", color = ShoeMuted,
                        fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                }
                items(catalog, key = { "catalog:${it.brand}:${it.model}" }) { choice ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = controlsEnabled, role = Role.Button,
                            onClickLabel = "Add ${choice.brand} ${choice.model}",
                            onClick = { onAdd(choice.brand, choice.model, null) })
                        .heightIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(choice.brand, color = ShoeMuted, fontSize = 11.sp)
                            Text(choice.model, color = ShoeInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }
                        Text("+", color = ShoeInk, fontSize = 26.sp, modifier = Modifier.padding(10.dp))
                    }
                }
                item {
                    if (catalog.isEmpty() && search.isNotEmpty()) Text("No catalog match. Add your pair below.",
                        color = ShoeMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    TextButton(onClick = { customOpen = !customOpen }, enabled = controlsEnabled,
                        modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp).testTag("shoeCustomToggle")) {
                        Text(if (customOpen) "Close custom pair" else "+ Add a different or second pair", color = ShoeInk)
                    }
                    if (customOpen) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShoeEntryField(brand, { brand = it.take(60) }, "Brand", "shoeCustomBrand", controlsEnabled)
                            ShoeEntryField(model, { model = it.take(80) }, "Model", "shoeCustomModel", controlsEnabled)
                            ShoeEntryField(nickname, { nickname = it.take(40) }, "Pair nickname (optional)", "shoeCustomNickname", controlsEnabled)
                            Text("A nickname helps tell two pairs of the same model apart.", color = ShoeMuted,
                                fontSize = 12.sp, lineHeight = 17.sp)
                            Button(onClick = { onAdd(brand.trim(), model.trim(), nickname.trim().takeIf { it.isNotEmpty() }) },
                                enabled = controlsEnabled && brand.isNotBlank() && model.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = ShoeInk, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("shoeAddCustom")) {
                                Text("Add and select pair")
                            }
                        }
                    }
                }
                if (shoes.any { it.archived }) {
                    item {
                        TextButton(onClick = { archivedOpen = !archivedOpen },
                            modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp).testTag("shoeArchivedToggle")) {
                            Text("${if (archivedOpen) "Hide" else "Show"} archived shoes", color = ShoeMuted)
                        }
                    }
                    if (archivedOpen) {
                        items(archived, key = { "archived:${it.id}" }) { shoe ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(shoe.fullName, color = ShoeInk, fontSize = 14.sp)
                                    Text(shoe.mileageLabel, color = ShoeMuted, fontSize = 12.sp)
                                }
                                TextButton(onClick = { onRestore(shoe.id) }, enabled = controlsEnabled,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("shoeRestore:${shoe.id}")) {
                                    Text("Restore", color = ShoeInk)
                                }
                            }
                        }
                        if (archived.isEmpty()) item { Text("No archived shoes match your search.",
                            color = ShoeMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun ShoeSectionLabel(text: String) {
    Text(text, color = ShoeMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 5.dp))
}

@Composable
private fun shoeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ShoeInk, unfocusedTextColor = ShoeInk,
    focusedBorderColor = Color(0xFF748B61), unfocusedBorderColor = Color(0xFFD6DDCE),
    focusedLabelColor = ShoeInk, unfocusedLabelColor = ShoeMuted,
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
)

@Composable
private fun ShoeEntryField(value: String, onChange: (String) -> Unit, label: String, tag: String, enabled: Boolean) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        enabled = enabled, singleLine = true, shape = RoundedCornerShape(14.dp), colors = shoeFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun SavedShoeRow(shoe: ShoePairDisplay, selected: Boolean, enabled: Boolean,
                         onSelect: () -> Unit, onArchive: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (selected) ShoeSage else Color.Transparent)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .testTag("shoeSelect:${shoe.id}").heightIn(min = 78.dp).padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Text(shoe.brand, color = ShoeMuted, fontSize = 11.sp)
            Text(shoe.model, color = ShoeInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            shoe.nickname?.takeIf { it.isNotBlank() }?.let { Text(it, color = ShoeMuted, fontSize = 12.sp) }
            Text(shoe.mileageLabel + if (selected) " · Selected" else "", color = ShoeMuted, fontSize = 12.sp)
        }
        TextButton(onClick = onArchive, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).testTag("shoeArchive:${shoe.id}")) {
            Text("Archive", color = ShoeMuted, fontSize = 12.sp)
        }
    }
}

/** Original neutral sneaker artwork, not a photograph of any catalog model. */
@Composable
internal fun ShoeIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun path(block: Path.() -> Unit) = Path().apply(block)
        val outline = path {
            moveTo(w * .47f, h * .035f)
            quadraticTo(w * .55f, h * .01f, w * .62f, h * .035f)
            lineTo(w * .67f, h * .045f)
            quadraticTo(w * .75f, h * .05f, w * .80f, h * .12f)
            lineTo(w * .85f, h * .15f)
            quadraticTo(w * .91f, h * .22f, w * .93f, h * .28f)
            lineTo(w * .925f, h * .32f)
            quadraticTo(w * .985f, h * .40f, w * .96f, h * .48f)
            lineTo(w * .97f, h * .56f)
            quadraticTo(w * .96f, h * .65f, w * .91f, h * .70f)
            lineTo(w * .89f, h * .75f)
            quadraticTo(w * .84f, h * .84f, w * .76f, h * .87f)
            lineTo(w * .70f, h * .91f)
            quadraticTo(w * .62f, h * .96f, w * .54f, h * .95f)
            lineTo(w * .46f, h * .965f)
            quadraticTo(w * .36f, h * .95f, w * .30f, h * .93f)
            lineTo(w * .25f, h * .90f)
            quadraticTo(w * .17f, h * .88f, w * .13f, h * .80f)
            lineTo(w * .095f, h * .76f)
            quadraticTo(w * .055f, h * .70f, w * .05f, h * .62f)
            lineTo(w * .03f, h * .56f)
            quadraticTo(w * .025f, h * .47f, w * .05f, h * .41f)
            lineTo(w * .055f, h * .34f)
            quadraticTo(w * .07f, h * .25f, w * .12f, h * .21f)
            lineTo(w * .15f, h * .16f)
            quadraticTo(w * .22f, h * .09f, w * .29f, h * .08f)
            lineTo(w * .36f, h * .05f)
            quadraticTo(w * .40f, h * .03f, w * .47f, h * .035f)
            close()
        }
        drawPath(outline, Color(0xFF789FAB))
        withTransform({ scale(.74f, .74f, Offset(w * .5f, h * .5f)) }) {
            drawPath(outline, Color(0xFFF7F7F7))
        }

        // Preserve breathing room inside the thicker ring without changing the control's target.
        withTransform({ scale(.90f, .90f, Offset(w * .5f, h * .5f)) }) {
            val upper = path {
                moveTo(w * .18f, h * .56f)
                cubicTo(w * .20f, h * .52f, w * .28f, h * .52f, w * .35f, h * .49f)
                cubicTo(w * .43f, h * .46f, w * .49f, h * .39f, w * .53f, h * .33f)
                lineTo(w * .59f, h * .34f)
                cubicTo(w * .62f, h * .38f, w * .63f, h * .44f, w * .69f, h * .44f)
                cubicTo(w * .74f, h * .45f, w * .77f, h * .40f, w * .80f, h * .39f)
                lineTo(w * .82f, h * .58f)
                cubicTo(w * .65f, h * .63f, w * .38f, h * .63f, w * .21f, h * .61f)
                cubicTo(w * .18f, h * .61f, w * .16f, h * .59f, w * .18f, h * .56f)
                close()
            }
            drawPath(upper, Color(0xFFA9A9A9))
            val sole = path {
                moveTo(w * .17f, h * .58f)
                cubicTo(w * .34f, h * .62f, w * .63f, h * .63f, w * .82f, h * .58f)
                lineTo(w * .82f, h * .64f)
                cubicTo(w * .65f, h * .69f, w * .35f, h * .68f, w * .21f, h * .65f)
                cubicTo(w * .18f, h * .64f, w * .17f, h * .61f, w * .17f, h * .58f)
                close()
            }
            drawPath(sole, Color.White)
            val outsole = path {
                moveTo(w * .18f, h * .62f)
                cubicTo(w * .35f, h * .66f, w * .64f, h * .67f, w * .82f, h * .62f)
                lineTo(w * .82f, h * .65f)
                cubicTo(w * .64f, h * .70f, w * .35f, h * .69f, w * .21f, h * .66f)
                cubicTo(w * .19f, h * .65f, w * .18f, h * .64f, w * .18f, h * .62f)
                close()
            }
            drawPath(outsole, Color(0xFFD3D3D3))
            repeat(3) { n ->
                val x = w * (.53f - n * .045f)
                val y = h * (.39f + n * .045f)
                drawLine(Color(0xFFF2F2F2), Offset(x, y), Offset(x + w * .055f, y + h * .025f),
                    w * .016f, StrokeCap.Round)
            }
        }
    }
}

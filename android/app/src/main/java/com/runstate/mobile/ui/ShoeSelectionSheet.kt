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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
                        ShoeIllustration(Modifier.size(58.dp), withBacking = false)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
        ShoeIllustration(Modifier.size(58.dp), withBacking = false)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
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
internal fun ShoeIllustration(modifier: Modifier = Modifier, withBacking: Boolean = true) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun path(block: Path.() -> Unit) = Path().apply(block)
        if (withBacking) {
            val outline = path {
                moveTo(w * .47f, h * .045f)
                cubicTo(w * .72f, h * .00f, w * .97f, h * .22f, w * .94f, h * .47f)
                cubicTo(w * .98f, h * .75f, w * .72f, h * .96f, w * .48f, h * .94f)
                cubicTo(w * .20f, h * .97f, w * .025f, h * .74f, w * .05f, h * .48f)
                cubicTo(w * .02f, h * .22f, w * .22f, h * .07f, w * .47f, h * .045f)
                close()
            }
            drawPath(outline, Brush.linearGradient(listOf(Color(0xFFB7CE72), Color(0xFF718A48)),
                start = Offset(w * .15f, 0f), end = Offset(w * .85f, h)))
            val inset = path {
                moveTo(w * .49f, h * .15f)
                cubicTo(w * .70f, h * .11f, w * .86f, h * .29f, w * .85f, h * .49f)
                cubicTo(w * .87f, h * .69f, w * .67f, h * .86f, w * .46f, h * .84f)
                cubicTo(w * .24f, h * .86f, w * .13f, h * .66f, w * .15f, h * .46f)
                cubicTo(w * .16f, h * .27f, w * .29f, h * .17f, w * .49f, h * .15f)
                close()
            }
            drawPath(inset, Color(0xFFD7E2AC).copy(alpha = .55f))
        }
        withTransform({ rotate(-12f, Offset(w * .5f, h * .54f)) }) {
            val sole = path {
                moveTo(w * .13f, h * .63f)
                cubicTo(w * .30f, h * .63f, w * .64f, h * .69f, w * .88f, h * .63f)
                cubicTo(w * .91f, h * .69f, w * .84f, h * .76f, w * .72f, h * .76f)
                lineTo(w * .21f, h * .73f)
                cubicTo(w * .14f, h * .72f, w * .12f, h * .68f, w * .13f, h * .63f)
                close()
            }
            drawPath(sole, Color(0xFFE9EDD8))
            val upper = path {
                moveTo(w * .14f, h * .63f)
                lineTo(w * .17f, h * .39f)
                cubicTo(w * .22f, h * .34f, w * .24f, h * .48f, w * .31f, h * .46f)
                lineTo(w * .40f, h * .32f)
                lineTo(w * .46f, h * .34f)
                cubicTo(w * .48f, h * .43f, w * .58f, h * .53f, w * .69f, h * .55f)
                cubicTo(w * .81f, h * .56f, w * .87f, h * .57f, w * .88f, h * .63f)
                cubicTo(w * .66f, h * .69f, w * .30f, h * .63f, w * .14f, h * .63f)
                close()
            }
            drawPath(upper, Color(0xFFFAFBF1))
            val panel = path {
                moveTo(w * .22f, h * .48f)
                lineTo(w * .33f, h * .51f)
                lineTo(w * .45f, h * .41f)
                lineTo(w * .58f, h * .55f)
                cubicTo(w * .47f, h * .56f, w * .39f, h * .62f, w * .23f, h * .60f)
                close()
            }
            drawPath(panel, Color(0xFF95A67A))
            drawLine(Color(0xFFB7D948), Offset(w * .17f, h * .64f), Offset(w * .56f, h * .67f), w * .045f, StrokeCap.Round)
            drawLine(Color(0xFF53673B), Offset(w * .19f, h * .72f), Offset(w * .72f, h * .75f), w * .026f, StrokeCap.Round)
            repeat(3) { n ->
                val x = w * (.39f + n * .045f)
                val y = h * (.40f + n * .045f)
                drawLine(Color(0xFF59684B), Offset(x, y), Offset(x - w * .07f, y + h * .035f), w * .017f, StrokeCap.Round)
            }
            val toeStitch = path {
                moveTo(w * .62f, h * .58f)
                cubicTo(w * .72f, h * .59f, w * .79f, h * .59f, w * .83f, h * .62f)
            }
            drawPath(toeStitch, Color(0xFFB6C1A5), style = Stroke(w * .012f, cap = StrokeCap.Round))
            drawLine(Color(0xFF53673B), Offset(w * .18f, h * .39f), Offset(w * .17f, h * .32f), w * .027f, StrokeCap.Round)
        }
    }
}

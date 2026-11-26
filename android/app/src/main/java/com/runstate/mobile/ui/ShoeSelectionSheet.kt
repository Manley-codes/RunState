package com.runstate.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity

/** Presentation only; identity, targets and measured mileage belong to the saved physical pair. */
internal data class ShoePairDisplay(
    val id: String,
    val brand: String,
    val model: String,
    val nickname: String? = null,
    val mileageLabel: String,
    val archived: Boolean = false,
    val goalDistanceMeters: Double? = null
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: model
    val fullName: String get() = listOfNotNull(brand, model, nickname?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

private val ShoeInk = Color(0xFF252925)
private val ShoeMuted = Color(0xFF737773)
private val ShoeSurface = Color.White
private val ShoeSage = Color(0xFFF0F3EF)

/** The owner closes this sheet only after an add or selection has actually saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoeSelectionSheet(
    shoes: List<ShoePairDisplay>,
    selectedShoeId: String?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String, Double) -> Unit,
    onAdd: (String, String, String?, Double) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onUndoArchive: () -> Unit,
    undoLabel: String? = null,
    error: String? = null,
    loading: Boolean = false,
    onRetryLoad: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val catalog = remember { ShoeCatalog.load(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedBrand by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var editingPairId by rememberSaveable { mutableStateOf<String?>(null) }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    val controlsEnabled = enabled && !loading
    val search = query.trim()
    fun matches(value: String) = search.isEmpty() || value.contains(search, true)
    fun sameBrand(value: String) = ShoeCatalog.brandKey(value) == selectedBrand?.let(ShoeCatalog::brandKey)
    fun openPair(pairBrand: String, pairModel: String, pair: ShoePairDisplay? = null, isCustom: Boolean = false) {
        brand = pairBrand; model = pairModel; nickname = pair?.nickname.orEmpty()
        goal = shoeGoalInput(pair?.goalDistanceMeters); editingPairId = pair?.id
        custom = isCustom; editing = true
    }
    fun back() {
        if (editing) { editing = false; editingPairId = null }
        else { selectedBrand = null; query = ""; archivedOpen = false }
    }
    val allBrands = remember(shoes) {
        ShoeCatalog.brands + shoes.map { it.brand }.distinctBy(ShoeCatalog::brandKey)
            .filter { saved -> ShoeCatalog.brands.none { ShoeCatalog.brandKey(it) == ShoeCatalog.brandKey(saved) } }
    }
    val shownBrands = allBrands.filter { candidate ->
        search.isEmpty() || ShoeCatalog.brandMatches(candidate, search) ||
            catalog.any { ShoeCatalog.brandKey(it.brand) == ShoeCatalog.brandKey(candidate) && matches(it.model) } ||
            shoes.any { ShoeCatalog.brandKey(it.brand) == ShoeCatalog.brandKey(candidate) && matches(it.fullName) }
    }
    val inBrand = shoes.filter { sameBrand(it.brand) && matches(it.fullName) }
    val active = inBrand.filterNot { it.archived }
    val archived = inBrand.filter { it.archived }
    val choices = catalog.filter { choice ->
        sameBrand(choice.brand) && (matches(choice.model) || ShoeCatalog.brandMatches(choice.brand, search)) &&
            shoes.none { ShoeCatalog.brandKey(it.brand) == ShoeCatalog.brandKey(choice.brand) && it.model.equals(choice.model, true) }
    }
    fun photo(pairBrand: String, pairModel: String) = catalog.firstOrNull {
        ShoeCatalog.brandKey(it.brand) == ShoeCatalog.brandKey(pairBrand) && it.model.equals(pairModel, true)
    }?.imageAsset
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ShoeSurface, contentColor = ShoeInk,
        modifier = modifier.testTag("shoeSheet")) {
        BackHandler(enabled = editing || selectedBrand != null) { back() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing || selectedBrand != null) TextButton(onClick = { back() },
                    enabled = controlsEnabled, modifier = Modifier.heightIn(min = 48.dp).testTag("shoeBack")) {
                    Text("Back", color = ShoeInk)
                }
                Text("Add Shoes", fontSize = 24.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close", color = ShoeMuted) }
            }
            if (!editing) OutlinedTextField(value = query, onValueChange = { query = it },
                placeholder = { Text(selectedBrand?.let { "Search $it" } ?: "Search brands or shoes") },
                singleLine = true, shape = RoundedCornerShape(50), colors = shoeFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp).testTag("shoeSearch"))
            if (loading) Text("Loading your shoes…", color = ShoeMuted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            if (error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = ShoeInk, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                if (onRetryLoad != null) TextButton(onClick = onRetryLoad, enabled = !loading) { Text("Retry", color = ShoeInk) }
            }
            if (undoLabel != null) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp)).background(ShoeSage).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(undoLabel, color = ShoeInk, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onUndoArchive, enabled = controlsEnabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("shoeUndo")) { Text("Undo", color = ShoeInk) }
            }
            // Each destination gets its own scroll position; opening a brand never starts mid-list.
            key(selectedBrand, editing) {
                val listState = rememberLazyListState()
                val listBoundary = remember {
                    object : NestedScrollConnection {
                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            // At the list's end, don't fling the expanded sheet upward into a bounce.
                            // Downward motion still reaches the sheet for normal drag-to-dismiss.
                            return Velocity(0f, minOf(0f, available.y))
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()
                    .nestedScroll(listBoundary).testTag("shoeList")) {
                    when {
                        editing -> {
                            item {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    if (custom) {
                                        Text("Add different shoes", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                        ShoeEntryField(brand, { brand = it.take(60) }, "Brand", "shoeCustomBrand", controlsEnabled)
                                        ShoeEntryField(model, { model = it.take(80) }, "Model", "shoeCustomModel", controlsEnabled)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val asset = photo(brand, model)
                                            if (asset != null) ShoeModelPhoto(asset)
                                            Column(Modifier.weight(1f).padding(start = if (asset != null) 18.dp else 0.dp)) {
                                                Text(brand, color = ShoeMuted, fontSize = 14.sp)
                                                Text(model, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                                                shoes.firstOrNull { it.id == editingPairId }?.let {
                                                    Text(it.mileageLabel, fontSize = 14.sp, color = ShoeMuted)
                                                }
                                            }
                                        }
                                    }
                                    OutlinedTextField(value = goal, onValueChange = { goal = it.take(12) },
                                        label = { Text("Mileage goal (required)") }, suffix = { Text("mi") },
                                        supportingText = { Text(if (goal.isNotBlank() && shoeGoalMeters(goal) == null)
                                            "Enter more than 0 and up to 100,000 miles." else "Your total-mileage target for this pair.") },
                                        isError = goal.isNotBlank() && shoeGoalMeters(goal) == null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true, enabled = controlsEnabled, shape = RoundedCornerShape(14.dp),
                                        colors = shoeFieldColors(), modifier = Modifier.fillMaxWidth().testTag("shoeGoal"))
                                    if (editingPairId == null) ShoeEntryField(nickname, { nickname = it.take(40) },
                                        "Pair nickname (optional)", "shoeCustomNickname", controlsEnabled)
                                    Button(onClick = {
                                        val storedGoal = shoes.firstOrNull { it.id == editingPairId }?.goalDistanceMeters
                                        val meters = if (storedGoal != null && goal == shoeGoalInput(storedGoal)) storedGoal
                                            else shoeGoalMeters(goal) ?: return@Button
                                        val pairId = editingPairId
                                        if (pairId != null) onSelect(pairId, meters)
                                        else onAdd(brand.trim(), model.trim(), nickname.trim().takeIf { it.isNotEmpty() }, meters)
                                    }, enabled = controlsEnabled && brand.isNotBlank() && model.isNotBlank() && shoeGoalMeters(goal) != null,
                                        colors = ButtonDefaults.buttonColors(containerColor = ShoeInk, contentColor = Color.White),
                                        shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                            .testTag("shoeAddCustom")) {
                                        Text(if (editingPairId == null) "Add and select" else "Save and select")
                                    }
                                }
                            }
                        }
                        selectedBrand == null -> {
                            item { Text("Select Your Brand", fontSize = 27.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 26.dp)) }
                            items(shownBrands, key = { "brand:$it" }) { value ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = controlsEnabled, role = Role.Button,
                                    onClick = { selectedBrand = value; query = "" })
                                    .heightIn(min = 68.dp).padding(horizontal = 24.dp, vertical = 16.dp).testTag("shoeBrand:$value"),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(value, fontSize = 20.sp, modifier = Modifier.weight(1f))
                                    Text("›", fontSize = 28.sp, color = ShoeInk)
                                }
                                HorizontalDivider(color = Color(0xFFE9E9E9))
                            }
                            if (shownBrands.isEmpty()) item { Text("No matching brands or shoes.", color = ShoeMuted,
                                modifier = Modifier.padding(24.dp)) }
                        }
                        else -> {
                            item { Text(selectedBrand.orEmpty(), fontSize = 27.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) }
                            items(active, key = { it.id }) { shoe ->
                                SavedShoeRow(shoe, photo(shoe.brand, shoe.model), shoe.id == selectedShoeId, controlsEnabled,
                                    onSelect = { openPair(shoe.brand, shoe.model, shoe) }, onArchive = { onArchive(shoe.id) })
                            }
                            items(choices, key = { "catalog:${it.brand}:${it.model}" }) { choice ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = controlsEnabled, role = Role.Button,
                                    onClick = { openPair(choice.brand, choice.model) })
                                    .padding(horizontal = 24.dp, vertical = 12.dp).testTag("shoeModel:${choice.model}"),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    if (choice.imageAsset != null) ShoeModelPhoto(choice.imageAsset)
                                    Text(choice.model, fontSize = 19.sp, modifier = Modifier.weight(1f)
                                        .heightIn(min = 52.dp).wrapContentHeight()
                                        .padding(start = if (choice.imageAsset != null) 20.dp else 0.dp))
                                }
                            }
                            if (active.isEmpty() && choices.isEmpty()) item { Text(
                                if (search.isEmpty()) "Add your model below." else "No matching shoes.", color = ShoeMuted,
                                modifier = Modifier.padding(24.dp)) }
                            if (shoes.any { sameBrand(it.brand) && it.archived }) {
                                item { TextButton(onClick = { archivedOpen = !archivedOpen }, modifier = Modifier.padding(horizontal = 12.dp)
                                    .heightIn(min = 48.dp).testTag("shoeArchivedToggle")) {
                                    Text(if (archivedOpen) "Hide archived shoes" else "Show archived shoes", color = ShoeMuted)
                                } }
                                if (archivedOpen) items(archived, key = { "archived:${it.id}" }) { shoe ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(shoe.displayName, fontSize = 17.sp)
                                            Text(shoe.mileageLabel, color = ShoeMuted, fontSize = 13.sp)
                                        }
                                        TextButton(onClick = { onRestore(shoe.id) }, enabled = controlsEnabled,
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("shoeRestore:${shoe.id}")) {
                                            Text("Restore", color = ShoeInk)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!editing) item {
                        TextButton(onClick = { openPair(selectedBrand.orEmpty(), "", isCustom = true) }, enabled = controlsEnabled,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp).heightIn(min = 48.dp).testTag("shoeCustomToggle")) {
                            Text("Add different shoes", color = ShoeInk, fontSize = 16.sp)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun shoeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ShoeInk, unfocusedTextColor = ShoeInk,
    focusedBorderColor = Color(0xFF789FAB), unfocusedBorderColor = Color(0xFFE1E3E1),
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
private fun SavedShoeRow(shoe: ShoePairDisplay, photo: String?, selected: Boolean, enabled: Boolean,
                         onSelect: () -> Unit, onArchive: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) ShoeSage else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = onSelect)
        .testTag("shoeSelect:${shoe.id}").padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (photo != null) ShoeModelPhoto(photo)
        Column(Modifier.weight(1f).padding(start = if (photo != null) 20.dp else 0.dp, end = 4.dp)) {
            Text(shoe.model, color = ShoeInk, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            shoe.nickname?.takeIf { it.isNotBlank() }?.let { Text(it, color = ShoeMuted, fontSize = 13.sp) }
            Text(shoe.mileageLabel + (shoe.goalDistanceMeters?.let { " / ${shoeGoalInput(it)} MI" } ?: "") +
                if (selected) " · Selected" else "", color = ShoeMuted, fontSize = 13.sp)
            TextButton(onClick = onArchive, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("shoeArchive:${shoe.id}")) {
                Text("Archive", color = ShoeMuted, fontSize = 12.sp)
            }
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
            moveTo(w * .47f, h * .045f)
            cubicTo(w * .72f, h * .00f, w * .97f, h * .22f, w * .94f, h * .47f)
            cubicTo(w * .98f, h * .75f, w * .72f, h * .96f, w * .48f, h * .94f)
            cubicTo(w * .20f, h * .97f, w * .025f, h * .74f, w * .05f, h * .48f)
            cubicTo(w * .02f, h * .22f, w * .22f, h * .07f, w * .47f, h * .045f)
            close()
        }
        // Match the music orb's charcoal/olive palette; keep the inner shape irregular.
        drawCircle(Color(0xFF1D1F16), radius = size.minDimension / 2f)
        withTransform({ scale(.74f, .74f, Offset(w * .5f, h * .5f)) }) {
            drawPath(outline, Color(0xFF7B8350))
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
            drawPath(upper, Color(0xFFC9CCBD))
            val sole = path {
                moveTo(w * .17f, h * .58f)
                cubicTo(w * .34f, h * .62f, w * .63f, h * .63f, w * .82f, h * .58f)
                lineTo(w * .82f, h * .64f)
                cubicTo(w * .65f, h * .69f, w * .35f, h * .68f, w * .21f, h * .65f)
                cubicTo(w * .18f, h * .64f, w * .17f, h * .61f, w * .17f, h * .58f)
                close()
            }
            drawPath(sole, Color(0xFFE7E9DE))
            val outsole = path {
                moveTo(w * .18f, h * .62f)
                cubicTo(w * .35f, h * .66f, w * .64f, h * .67f, w * .82f, h * .62f)
                lineTo(w * .82f, h * .65f)
                cubicTo(w * .64f, h * .70f, w * .35f, h * .69f, w * .21f, h * .66f)
                cubicTo(w * .19f, h * .65f, w * .18f, h * .64f, w * .18f, h * .62f)
                close()
            }
            drawPath(outsole, Color(0xFFA2AB8A))
            repeat(3) { n ->
                val x = w * (.53f - n * .045f)
                val y = h * (.39f + n * .045f)
                drawLine(Color(0xFFF2F2E8), Offset(x, y), Offset(x + w * .055f, y + h * .025f),
                    w * .016f, StrokeCap.Round)
            }
        }
    }
}

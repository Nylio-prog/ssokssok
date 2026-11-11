package com.nylio.ssokssok.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.AnswerMode
import com.nylio.ssokssok.data.StudyConfig
import com.nylio.ssokssok.data.StudyItem
import com.nylio.ssokssok.data.db.CardState
import com.nylio.ssokssok.data.db.Direction
import com.nylio.ssokssok.korean.CheckResult
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.korean.Hanja
import com.nylio.ssokssok.korean.HanjaPart
import com.nylio.ssokssok.korean.DiffKind
import com.nylio.ssokssok.korean.DiffSegment
import com.nylio.ssokssok.korean.Verdict
import com.nylio.ssokssok.srs.Rating
import com.nylio.ssokssok.ui.appViewModel
import com.nylio.ssokssok.ui.components.HanjaFamilySheet
import com.nylio.ssokssok.ui.components.Pill
import com.nylio.ssokssok.ui.components.SlimProgress
import com.nylio.ssokssok.ui.theme.Jakarta
import com.nylio.ssokssok.ui.theme.KoreanDisplay
import com.nylio.ssokssok.ui.theme.Ssok
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(container: AppContainer, nav: NavController, config: StudyConfig) {
    val vm = appViewModel(container, key = config.toString()) { ReviewViewModel(it, config) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var hanjaPart by remember { mutableStateOf<HanjaPart?>(null) }

    BackHandler { nav.popBackStack() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        TopRow(ui, practice = vm.isPractice, onClose = { nav.popBackStack() }, onUndo = vm::undo)

        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.finished -> SessionSummary(
                ui = ui,
                practice = vm.isPractice,
                onDone = { nav.popBackStack() },
                onMore = vm::restart,
            )
            ui.current != null -> {
                val item = ui.current!!
                // Pronounce Korean when it is on screen: right away for Korean prompts, after the flip otherwise.
                LaunchedEffect(ui.turn, ui.revealed) {
                    if (!settings.autoplayAudio) return@LaunchedEffect
                    val koreanPrompt = item.direction == Direction.RECOGNIZE && !item.cloze
                    if ((koreanPrompt && !ui.revealed) || (!koreanPrompt && ui.revealed)) {
                        vm.speak(Cloze.plain(item.card.korean))
                    }
                }
                StudyArea(
                    ui = ui,
                    item = item,
                    mode = config.answerMode,
                    onHanja = { hanjaPart = it },
                    onReveal = { vm.reveal() },
                    onGiveUp = { vm.reveal(giveUp = true) },
                    onTyped = vm::onTyped,
                    onSpeak = { vm.speak(item.card.korean) },
                    onSpeakExample = { vm.speak(item.card.example) },
                    onRate = { r ->
                        if (settings.haptics) {
                            haptic.performHapticFeedback(
                                if (r == Rating.HARD) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                            )
                        }
                        vm.rate(r)
                    },
                )
                hanjaPart?.let { part ->
                    HanjaFamilySheet(container.repository, part, item.card.id, onDismiss = { hanjaPart = null })
                }
            }
        }
    }
}

@Composable
private fun TopRow(ui: ReviewUi, practice: Boolean, onClose: () -> Unit, onUndo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "End session") }
        val total = ui.done + ui.remaining
        SlimProgress(
            progress = if (total == 0) 0f else ui.done.toFloat() / total,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            height = 8.dp,
        )
        Text(
            if (practice) "Practice · ${ui.remaining}" else "${ui.remaining} left",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = onUndo, enabled = ui.canUndo) {
            Icon(Icons.AutoMirrored.Outlined.Undo, "Undo last answer")
        }
    }
}

@Composable
private fun StudyArea(
    ui: ReviewUi,
    item: StudyItem,
    mode: AnswerMode,
    onHanja: (HanjaPart) -> Unit,
    onReveal: () -> Unit,
    onGiveUp: () -> Unit,
    onTyped: (String) -> Unit,
    onSpeak: () -> Unit,
    onSpeakExample: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Each card keeps its own flip and swipe state, so the outgoing card animates out as itself.
        AnimatedContent(
            targetState = CardSnap(ui.turn, item),
            contentKey = { it.turn },
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 14 }) togetherWith fadeOut(tween(140))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "card",
        ) { snap ->
            val current = snap.turn == ui.turn
            FlashCard(
                item = snap.item,
                revealed = if (current) ui.revealed else true,
                check = if (current) ui.check else null,
                interactive = current,
                mode = mode,
                onHanja = onHanja,
                onReveal = onReveal,
                onSpeak = onSpeak,
                onSpeakExample = onSpeakExample,
                onRate = onRate,
            )
        }

        // ------------------------------------------------ controls
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp)) {
            when {
                ui.revealed -> RatingBar(ui, onRate)
                mode == AnswerMode.TYPE -> TypeAnswer(item, ui.typed, ui.turn, onTyped, onReveal, onGiveUp)
                else -> FilledTonalButton(
                    onClick = onReveal,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Show answer", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

private data class CardSnap(val turn: Int, val item: StudyItem)

@Composable
private fun FlashCard(
    item: StudyItem,
    revealed: Boolean,
    check: CheckResult?,
    interactive: Boolean,
    mode: AnswerMode,
    onHanja: (HanjaPart) -> Unit,
    onReveal: () -> Unit,
    onSpeak: () -> Unit,
    onSpeakExample: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    val density = LocalDensity.current
    val flip by animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val swipeThreshold = with(density) { 110.dp.toPx() }
    val swipeColor = when {
        dragX.value > 0 -> Ssok.colors.easy
        dragX.value < 0 -> Ssok.colors.hard
        else -> Color.Transparent
    }
    val swipeAmount = (abs(dragX.value) / swipeThreshold).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(
                if (swipeAmount > 0f) 2.dp else 1.dp,
                if (swipeAmount > 0f) swipeColor.copy(alpha = 0.3f + 0.7f * swipeAmount) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            // Gestures sit outside the flip: inside a layer rotated 180° they would be mirrored.
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when {
                        !interactive -> Modifier
                        revealed -> Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val v = dragX.value
                                    scope.launch {
                                        when {
                                            v > swipeThreshold -> {
                                                dragX.animateTo(swipeThreshold * 4, tween(160))
                                                onRate(Rating.EASY)
                                            }
                                            v < -swipeThreshold -> {
                                                dragX.animateTo(-swipeThreshold * 4, tween(160))
                                                onRate(Rating.HARD)
                                            }
                                            else -> dragX.animateTo(0f, spring())
                                        }
                                    }
                                },
                                onDragCancel = { scope.launch { dragX.animateTo(0f, spring()) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { dragX.snapTo(dragX.value + amount) }
                            }
                        }
                        else -> Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = mode == AnswerMode.FLIP,
                            onClick = onReveal,
                        )
                    },
                )
                .graphicsLayer {
                    translationX = dragX.value
                    rotationZ = dragX.value / 60f
                }
                .graphicsLayer {
                    rotationY = flip
                    cameraDistance = 14f * density.density
                },
        ) {
            if (flip <= 90f) {
                CardFront(item, onSpeak)
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    CardBack(item, check, onHanja, onSpeak, onSpeakExample)
                }
            }
        }
        if (swipeAmount > 0.15f) {
            Pill(
                text = if (dragX.value > 0) "Easy" else "Hard",
                container = swipeColor,
                content = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .graphicsLayer { alpha = swipeAmount },
            )
        }
    }
}

// ---------------------------------------------------------------- card faces

private fun koreanSize(text: String) = when (text.length) {
    in 0..4 -> 60.sp
    in 5..8 -> 46.sp
    in 9..16 -> 36.sp
    in 17..30 -> 28.sp
    else -> 24.sp
}

private fun englishSize(text: String) = when (text.length) {
    in 0..14 -> 34.sp
    in 15..32 -> 27.sp
    else -> 22.sp
}

@Composable
private fun CardHeader(item: StudyItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(item.card.cardType.label)
        val dir = when {
            item.cloze -> "Fill the blank"
            item.direction == Direction.RECOGNIZE -> "Korean → English"
            else -> "English → Korean"
        }
        Text(dir, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (item.isNew) {
            Pill("NEW", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
        } else if (item.progress.state == CardState.RELEARNING || item.progress.lapses >= 3) {
            Pill("TRICKY", container = Ssok.colors.hardContainer, content = Ssok.colors.hard)
        }
    }
}

@Composable
private fun CardFront(item: StudyItem, onSpeak: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        CardHeader(item)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(12.dp))
            when {
                item.cloze -> {
                    ClozeText(item.card.korean, revealed = false)
                    val hints = Cloze.parts(item.card.korean).mapNotNull { it.hint }
                    if (hints.isNotEmpty()) {
                        Text(
                            "Hint: ${hints.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Text(
                        item.card.english,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item.direction == Direction.RECOGNIZE -> {
                    Text(
                        item.card.korean,
                        fontFamily = KoreanDisplay,
                        fontSize = koreanSize(item.card.korean),
                        lineHeight = koreanSize(item.card.korean) * 1.25f,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SpeakButton(onSpeak, Modifier.padding(top = 12.dp))
                }
                else -> {
                    Text(
                        item.card.english,
                        fontFamily = Jakarta,
                        fontWeight = FontWeight.Bold,
                        fontSize = englishSize(item.card.english),
                        lineHeight = englishSize(item.card.english) * 1.2f,
                        textAlign = TextAlign.Center,
                    )
                    if (item.card.cardType == com.nylio.ssokssok.data.db.CardType.GRAMMAR) {
                        Text(
                            "Give the Korean pattern",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CardBack(
    item: StudyItem,
    check: CheckResult?,
    onHanja: (HanjaPart) -> Unit,
    onSpeak: () -> Unit,
    onSpeakExample: () -> Unit,
) {
    val card = item.card
    Column(
        Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CardHeader(item)
        if (check != null) VerdictBanner(check)

        // Prompt, small
        when {
            item.cloze -> Unit
            item.direction == Direction.RECOGNIZE -> Text(
                card.korean, fontFamily = KoreanDisplay, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(card.english, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Answer, large
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                item.cloze -> {
                    ClozeText(card.korean, revealed = true, align = TextAlign.Start)
                    Text(card.english, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.direction == Direction.RECOGNIZE -> Text(
                    card.english,
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = englishSize(card.english), lineHeight = englishSize(card.english) * 1.2f,
                )
                else -> Text(
                    card.korean, fontFamily = KoreanDisplay,
                    fontSize = koreanSize(card.korean), lineHeight = koreanSize(card.korean) * 1.25f,
                )
            }
            SpeakButton(onSpeak, Modifier.padding(top = 4.dp))
        }

        val hanja = remember(card.hanja) { Hanja.parse(card.hanja) }
        if (hanja.isNotEmpty()) HanjaRow(hanja, onHanja)

        if (card.example.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        Cloze.plain(card.example),
                        fontFamily = KoreanDisplay, fontSize = 19.sp, lineHeight = 26.sp,
                    )
                    if (card.exampleTranslation.isNotBlank()) {
                        Text(card.exampleTranslation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onSpeakExample) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Play example", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (card.notes.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text(card.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

/** One chip per Sino-Korean syllable: reading big, 훈 below, character small. Tap to see related words. */
@Composable
fun HanjaRow(parts: List<HanjaPart>, onClick: ((HanjaPart) -> Unit)?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            Surface(
                onClick = { onClick?.invoke(part) },
                enabled = onClick != null,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        part.eum.ifEmpty { part.char },
                        fontFamily = KoreanDisplay,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            part.hun.ifEmpty { part.eum },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val sub = listOf(part.char, part.english).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Listen", Modifier.size(18.dp))
            Text("Listen", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ClozeText(source: String, revealed: Boolean, align: TextAlign = TextAlign.Center) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val text: AnnotatedString = buildAnnotatedString {
        for (part in Cloze.parts(source)) {
            if (!part.isBlank) {
                append(part.text)
            } else if (revealed) {
                withStyle(SpanStyle(color = primary, background = container, fontWeight = FontWeight.Bold)) { append(part.text) }
            } else {
                withStyle(SpanStyle(color = primary, background = container)) {
                    append(" " + "＿".repeat(part.text.length.coerceIn(1, 4)) + " ")
                }
            }
        }
    }
    val size = koreanSize(Cloze.plain(source)).value.coerceAtMost(34f).sp
    Text(text, fontFamily = KoreanDisplay, fontSize = size, lineHeight = size * 1.35f, textAlign = align)
}

@Composable
private fun VerdictBanner(check: CheckResult) {
    val c = Ssok.colors
    val (fg, bg, title, icon) = when (check.verdict) {
        Verdict.CORRECT -> Quad(c.easy, c.easyContainer, "Correct", Icons.Outlined.Check)
        Verdict.CLOSE -> Quad(c.medium, c.mediumContainer, "Almost", Icons.Outlined.ErrorOutline)
        Verdict.WRONG -> Quad(c.hard, c.hardContainer, if (check.yours.isEmpty()) "Here it is" else "Not quite", Icons.Outlined.Close)
    }
    Surface(color = bg, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            }
            if (check.verdict != Verdict.CORRECT && check.yours.isNotEmpty()) {
                DiffLine("You", check.yours, fg)
                DiffLine("Answer", check.target, c.easy)
            }
            check.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun DiffLine(label: String, segments: List<DiffSegment>, accent: Color) {
    val text = buildAnnotatedString {
        segments.forEach { seg ->
            when (seg.kind) {
                DiffKind.SAME -> append(seg.text)
                DiffKind.WRONG -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(seg.text) }
                DiffKind.EXTRA -> withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                DiffKind.MISSING -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(seg.text) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(text, fontFamily = KoreanDisplay, fontSize = 20.sp)
    }
}

// ---------------------------------------------------------------- controls

@Composable
private fun TypeAnswer(
    item: StudyItem,
    typed: String,
    turn: Int,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
) {
    val expect = item.expectation()
    val focus = remember { FocusRequester() }
    LaunchedEffect(turn) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = onTyped,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = {
                Text(if (expect.korean) "한국어로 입력하세요" else "Type the meaning in English")
            },
            textStyle = if (expect.korean) {
                TextStyle(fontFamily = KoreanDisplay, fontSize = 22.sp)
            } else {
                MaterialTheme.typography.titleMedium
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onGiveUp, modifier = Modifier.height(52.dp)) { Text("I don't know") }
            Button(
                onClick = onSubmit,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Check", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun RatingBar(ui: ReviewUi, onRate: (Rating) -> Unit) {
    val c = Ssok.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Rating.entries.forEach { r ->
                val (fg, bg) = when (r) {
                    Rating.HARD -> c.hard to c.hardContainer
                    Rating.MEDIUM -> c.medium to c.mediumContainer
                    Rating.EASY -> c.easy to c.easyContainer
                }
                val suggested = ui.suggested == r
                Surface(
                    onClick = { onRate(r) },
                    color = if (suggested) fg else bg,
                    contentColor = if (suggested) MaterialTheme.colorScheme.surface else fg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 78.dp),
                ) {
                    Column(
                        Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(r.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            r.hint,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = LocalContentColorAlpha(0.8f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ui.previews[r]?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = LocalContentColorAlpha(0.8f))
                        }
                    }
                }
            }
        }
        Text(
            "Swipe the card: right for Easy, left for Hard",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LocalContentColorAlpha(alpha: Float): Color =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = alpha)

// ---------------------------------------------------------------- summary

@Composable
private fun SessionSummary(ui: ReviewUi, practice: Boolean, onDone: () -> Unit, onMore: () -> Unit) {
    val c = Ssok.colors
    val total = ui.counts.values.sum()
    val known = (ui.counts[Rating.EASY] ?: 0) + (ui.counts[Rating.MEDIUM] ?: 0)
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(if (total == 0) "텅 비었어요" else "수고했어요!", fontFamily = KoreanDisplay, fontSize = 40.sp)
        Text(
            if (total == 0) "Nothing to study here right now." else "Good work. That's the session.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (total > 0) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryNumber("$total", "answers")
                        SummaryNumber("${(known * 100f / total).roundToInt()}%", "recalled")
                        val minutes = ui.sessionMs / 60_000
                        val seconds = (ui.sessionMs / 1000) % 60
                        SummaryNumber(if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s", "time")
                    }
                    // Proportion bar of the three answers
                    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                        Rating.entries.forEach { r ->
                            val n = ui.counts[r] ?: 0
                            if (n > 0) {
                                Box(
                                    Modifier.weight(n.toFloat()).fillMaxSize().background(
                                        when (r) { Rating.HARD -> c.hard; Rating.MEDIUM -> c.medium; Rating.EASY -> c.easy },
                                    ),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Rating.entries.forEach { r ->
                            val color = when (r) { Rating.HARD -> c.hard; Rating.MEDIUM -> c.medium; Rating.EASY -> c.easy }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                                Text("${r.label} ${ui.counts[r] ?: 0}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            if (practice) {
                Text(
                    "Practice run: your schedule was not changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) {
            Text("Keep going")
        }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

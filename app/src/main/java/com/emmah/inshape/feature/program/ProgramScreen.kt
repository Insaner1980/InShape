package com.emmah.inshape.feature.program

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.emmah.inshape.R
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.ui.components.DisclosureRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme

@Composable
internal fun ProgramList(catalog: ProgramCatalog, onProgram: (String) -> Unit) {
    Text(stringResource(R.string.programs), style = MaterialTheme.typography.titleLarge)
    ListCard {
        catalog.programs.forEach { program ->
            item {
                ListRow(
                    program.name,
                    subtitle = stringResource(
                        R.string.program_frequency,
                        program.allowedWeeklySessions.joinToString("–"),
                        program.estimatedSessionMinutes.min,
                        program.estimatedSessionMinutes.max
                    ),
                    onClick = {
                        onProgram(program.id)
                    }
                )
            }
        }
    }
}

@Composable
internal fun ProgramScreen(
    catalog: ProgramCatalog,
    program: TrainingProgram?,
    enrolled: Boolean,
    onUse: () -> Unit,
    onRestart: () -> Unit,
    onEdit: (String) -> Unit,
    onGuide: (String, String) -> Unit
) {
    var showGuidance by rememberSaveable(program?.id) { mutableStateOf(false) }
    Page(
        bottomBar = if (program == null) {
            null
        } else {
            {
                PrimaryButton(
                    stringResource(if (enrolled) R.string.review_program_workout else R.string.use_program),
                    onUse,
                    Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        if (program == null) {
            Text(stringResource(R.string.program_unavailable))
            return@Page
        }
        Text(program.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(
                R.string.program_frequency,
                program.allowedWeeklySessions.joinToString("–"),
                program.estimatedSessionMinutes.min,
                program.estimatedSessionMinutes.max
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = InShapeTheme.colors.textSecondary
        )
        OutlineButton(stringResource(R.string.edit_program_days), { onEdit(program.sequence.first()) }, Modifier.fillMaxWidth())
        program.sessions.forEach { day ->
            Text(stringResource(R.string.program_day_title, day.id, day.name), style = MaterialTheme.typography.titleLarge)
            ListCard {
                day.exercises.forEach { slot ->
                    item {
                        ListRow(
                            slot.name.exerciseDisplayName(LocalContext.current),
                            subtitle = stringResource(
                                if (slot.target.perSide) R.string.program_slot_per_side else R.string.program_slot,
                                slot.sets,
                                slot.target.range
                            ),
                            onClick = { onGuide(day.id, slot.exerciseId) }
                        )
                    }
                }
            }
        }
        DisclosureRow(stringResource(R.string.program_more_information), showGuidance, { showGuidance = !showGuidance })
        if (showGuidance) {
            ProgramGuidance(catalog, program)
            if (enrolled) OutlineButton(stringResource(R.string.restart_training_program), onRestart)
        }
    }
}

@Composable
private fun ColumnScope.ProgramGuidance(catalog: ProgramCatalog, program: TrainingProgram) {
    Text(stringResource(R.string.category_warm_up), style = MaterialTheme.typography.titleMedium)
    with(catalog.warmup) {
        Text(stringResource(R.string.program_warmup_advice, minutes, to, suggestions.joinToString(", ")))
        Text(stringResource(R.string.optional_mobility, optionalMobilityMinutes))
    }
    Text(stringResource(R.string.cool_down), style = MaterialTheme.typography.titleMedium)
    with(catalog.coolDown) { Text(stringResource(R.string.program_cooldown_advice, minutes, to)) }
    Text(stringResource(R.string.short_workout), style = MaterialTheme.typography.titleMedium)
    program.sessions.forEach { day ->
        val mini = day.miniExerciseIds.mapNotNull { key -> day.exercises.find { it.exerciseId == key }?.name }
        Text(stringResource(R.string.mini_program_sets, day.id, mini.joinToString(", ")))
    }
    Text(stringResource(R.string.eight_week_plan), style = MaterialTheme.typography.titleMedium)
    program.phases.forEach { phase ->
        val weeks = listOf(phase.weeks.first(), phase.weeks.last()).distinct().joinToString("–")
        Text(stringResource(R.string.program_weeks, weeks, phase.sets, phase.effort))
    }
    val restDays = catalog.scheduling.restDaysBetweenStrengthSessionsMinimum
    Text(pluralStringResource(R.plurals.program_rest_advice, restDays, restDays))
    Text(stringResource(R.string.program_safety))
    Text(stringResource(R.string.sources), style = MaterialTheme.typography.titleMedium)
    catalog.sources.forEach { source -> Text(source.name) }
}

@Composable
internal fun ProgramGuideScreen(catalog: ProgramCatalog, key: String, slot: ProgramSlot? = null, onAlternative: (String) -> Unit) {
    val name = catalog.guideName(key)
    val artwork = programArtwork(key)
    var enlarged by rememberSaveable(key) { mutableStateOf(false) }
    var zoom by rememberSaveable(key) { mutableFloatStateOf(1f) }
    var x by rememberSaveable(key) { mutableFloatStateOf(0f) }
    var y by rememberSaveable(key) { mutableFloatStateOf(0f) }
    Page {
        Text(name ?: stringResource(R.string.exercise_guide), style = MaterialTheme.typography.headlineMedium)
        if (artwork != null && name != null) {
            Image(
                painterResource(artwork),
                contentDescription = stringResource(R.string.movement_illustration, name),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(InShapeShapes.card)
                    .clickable(onClickLabel = stringResource(if (enlarged) R.string.fit_guide else R.string.enlarge_guide)) {
                        enlarged = !enlarged
                        zoom = if (enlarged) 2f else 1f
                        x = 0f
                        y = 0f
                    }
                    .pointerInput(enlarged) {
                        if (enlarged) {
                            detectTransformGestures { _, pan: Offset, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 4f)
                                val limit = size.width * (zoom - 1f) / 2f
                                x = (x + pan.x).coerceIn(-limit, limit)
                                y = (y + pan.y).coerceIn(-limit, limit)
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = x
                        translationY = y
                    }
            )
            if (slot == null) {
                catalog.programs.flatMap { it.sessions }.flatMap { it.exercises }
                    .filter { it.exerciseId == key }.map { it.instruction }.filter(String::isNotBlank)
                    .distinct().forEach { Text(it) }
            } else {
                if (slot.instruction.isNotBlank()) Text(slot.instruction)
                if (slot.restSeconds > 0) {
                    Text(
                        stringResource(R.string.program_guide_rest, slot.restSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InShapeTheme.colors.textSecondary
                    )
                }
                if (slot.alternatives.isNotEmpty()) {
                    val label = stringResource(
                        if (slot.alternatives.size == 1) R.string.program_alternative_label else R.string.program_guide_alternatives
                    )
                    val color = InShapeTheme.colors.textSecondary
                    Text(
                        buildAnnotatedString {
                            append(label)
                            append(' ')
                            slot.alternatives.forEachIndexed { index, alternative ->
                                if (index > 0) append(", ")
                                withLink(
                                    LinkAnnotation.Clickable(
                                        alternative.exerciseId,
                                        TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                                    ) { onAlternative(alternative.exerciseId) }
                                ) { append(alternative.label) }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        } else {
            Text(stringResource(R.string.guide_unavailable))
        }
    }
}

package com.emmah.inshape.feature.program

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import com.emmah.inshape.ui.components.DisclosureRow
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.components.PrimaryButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.emmah.inshape.ui.theme.InShapeShapes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.Page

@Composable
internal fun ProgramList(catalog: ProgramCatalog, onProgram: (String) -> Unit) {
    Text(stringResource(R.string.programs), style = MaterialTheme.typography.titleLarge)
    ListCard {
        catalog.programs.forEach { program ->
            item {
                ListRow(program.name, subtitle = stringResource(R.string.program_frequency, program.allowedWeeklySessions.joinToString("–"), program.estimatedSessionMinutes.min, program.estimatedSessionMinutes.max), onClick = { onProgram(program.id) })
            }
        }
    }
}

@Composable
internal fun ProgramScreen(catalog: ProgramCatalog, programId: String, onUse: () -> Unit, onGuide: (String) -> Unit) {
    val program = catalog.program(programId)
    var showGuidance by rememberSaveable(programId) { mutableStateOf(false) }
    Page(bottomBar = if (program == null) null else {
        { PrimaryButton(stringResource(R.string.use_program), onUse, Modifier.fillMaxWidth()) }
    }) {
        if (program == null) {
            Text(stringResource(R.string.program_unavailable))
            return@Page
        }
        Text(program.name, style = MaterialTheme.typography.headlineMedium)
        Text(program.description)
        Text(stringResource(
            R.string.program_frequency,
            program.allowedWeeklySessions.joinToString("–"),
            program.estimatedSessionMinutes.min,
            program.estimatedSessionMinutes.max
        ))
        if (program.sessions.size > 1) Text(stringResource(R.string.program_rotation, program.sequence.joinToString(" → ")))
        program.sessions.forEach { day ->
            Text(stringResource(R.string.program_day_title, day.id, day.name), style = MaterialTheme.typography.titleLarge)
            ListCard {
                day.exercises.forEach { slot ->
                    item {
                        ListRow(
                            slot.name,
                            subtitle = stringResource(R.string.program_slot, slot.sets, slot.target.localizedDisplay(LocalContext.current), slot.restSeconds) +
                                if (slot.optional) ", " + stringResource(R.string.optional) else "",
                            onClick = { onGuide(slot.exerciseId) },
                            details = {
                                val style = MaterialTheme.typography.bodyMedium
                                val color = InShapeTheme.colors.textSecondary
                                if (slot.instruction.isNotBlank()) {
                                    Text(slot.instruction, style = style, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                slot.alternatives.forEach { alternative ->
                                    Text(
                                        stringResource(R.string.program_alternative_label, alternative.label),
                                        Modifier.clickable { onGuide(alternative.exerciseId) },
                                        style = style,
                                        color = color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        )
                    }
                }
            }
            val mini = day.miniExerciseIds.mapNotNull { key -> day.exercises.find { it.exerciseId == key }?.name }
            Text(stringResource(R.string.short_workout), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.mini_program_sets, mini.joinToString(", ")))
        }
        DisclosureRow(stringResource(R.string.program_more_information), showGuidance, { showGuidance = !showGuidance })
        if (showGuidance) ProgramGuidance(catalog, program)
    }
}

@Composable
private fun ProgramGuidance(catalog: ProgramCatalog, program: TrainingProgram) {
    Text(stringResource(R.string.category_warm_up), style = MaterialTheme.typography.titleLarge)
    with(catalog.warmup) {
        Text(stringResource(R.string.program_warmup_advice, minutes, to, suggestions.joinToString(", "), note))
        Text(stringResource(R.string.optional_mobility, optionalMobilityMinutes))
    }
    Text(stringResource(R.string.cool_down), style = MaterialTheme.typography.titleLarge)
    with(catalog.coolDown) { Text(stringResource(R.string.program_cooldown_advice, minutes, to, note)) }
    Text(stringResource(R.string.eight_week_plan), style = MaterialTheme.typography.titleLarge)
    val context = LocalContext.current
    program.phases.forEach { phase ->
        val weeks = listOf(phase.weeks.first(), phase.weeks.last()).distinct().joinToString("–")
        Text(stringResource(R.string.program_weeks, weeks), style = MaterialTheme.typography.titleMedium)
        Text(listOf(phase.sets, phase.effort.localizedProgramEffort(context), phase.goal).joinToString("\n"))
    }
    Text(catalog.effort.localizedProgramEffort(context))
    Text(stringResource(R.string.rest), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.program_rest_advice, catalog.scheduling.restDaysBetweenStrengthSessionsMinimum))
    Text(catalog.scheduling.missedWorkout)
    Text(catalog.scheduling.switchPlan)
    Text(stringResource(R.string.progression), style = MaterialTheme.typography.titleLarge)
    with(catalog.progression) {
        Text(whenToSuggestMoreLoad)
        Text(suggestion)
        Text(ifNotReady)
        Text(deload)
    }
    Text(catalog.safety)
    Text(catalog.notice)
    Text(stringResource(R.string.sources), style = MaterialTheme.typography.titleMedium)
    catalog.sources.forEach { source ->
        Text(source.name)
        source.note?.let { Text(it) }
    }
}

@Composable
internal fun ProgramGuideScreen(catalog: ProgramCatalog, key: String) {
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
                        if (enlarged) detectTransformGestures { _, pan: Offset, scale, _ ->
                            zoom = (zoom * scale).coerceIn(1f, 4f)
                            val limit = size.width * (zoom - 1f) / 2f
                            x = (x + pan.x).coerceIn(-limit, limit)
                            y = (y + pan.y).coerceIn(-limit, limit)
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = x
                        translationY = y
                    }
            )
            catalog.programs.flatMap { it.sessions }.flatMap { it.exercises }
                .filter { it.exerciseId == key }.map { it.instruction }.filter(String::isNotBlank)
                .distinct().forEach { Text(it) }
        } else {
            Text(stringResource(R.string.guide_unavailable))
        }
    }
}

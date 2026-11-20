package com.emmah.inshape.feature.checkin

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.emmah.inshape.ui.theme.finnishLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.EditorSheet
import com.emmah.inshape.ui.components.IconCircleButton
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.observationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PainInput(
    checkInId: String,
    observations: List<BodyObservation>,
    saving: Boolean,
    onChange: (List<BodyObservation>) -> Unit,
    legacyNoteRegions: List<String> = emptyList(),
    previous: List<BodyObservation> = emptyList()
) {
    var showBody by rememberSaveable(checkInId) { mutableStateOf(observations.isNotEmpty()) }
    var back by rememberSaveable(checkInId) { mutableStateOf(false) }
    var picker by rememberSaveable(checkInId) { mutableStateOf(false) }
    var clearAreas by rememberSaveable(checkInId) { mutableStateOf(false) }
    var region by rememberSaveable(checkInId) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val colors = InShapeTheme.colors

    fun addArea(id: String) {
        if (saving) return
        if (observations.any { it.region == id }) {
            region = id
        } else {
            onChange(observations + BodyObservation(checkInId, id, BodyLevel.Mild.stored, BodyLevel.None.stored))
        }
    }

    Text(stringResource(R.string.pain_question), style = MaterialTheme.typography.titleMedium)
    SegmentedControl(listOf(stringResource(R.string.no), stringResource(R.string.yes)), if (showBody) 1 else 0, {
        if (!saving) {
            if (it == 0 && observations.isNotEmpty()) clearAreas = true else showBody = it == 1
        }
    })
    if (showBody) {
        SegmentedControl(listOf(stringResource(R.string.body_front), stringResource(R.string.body_back)), if (back) 1 else 0, { back = it == 1 })
        BodyMap(back, observations, ::addArea)
        TextAction(stringResource(R.string.pick_from_list), { picker = true }, icon = Icons.AutoMirrored.Outlined.List, enabled = !saving)
        for (row in observations) {
            val label = stringResource(bodyRegionLabel(row.region))
            Surface(shape = InShapeShapes.field, color = colors.surface) {
                ListRow(
                    label,
                    modifier = Modifier.padding(horizontal = Dimens.inner),
                    details = {
                        val symptoms = listOfNotNull(
                            bodyLevelOf(row.pain)?.takeUnless { it == BodyLevel.None }?.let { stringResource(R.string.pain) to it },
                            bodyLevelOf(row.stiffness)?.takeUnless { it == BodyLevel.None }?.let { stringResource(R.string.stiffness).lowercase(finnishLocale) to it }
                        )
                        val summary = buildAnnotatedString {
                            if (symptoms.isEmpty()) {
                                append(observationSummary(context, row))
                            } else {
                                symptoms.forEachIndexed { index, (metric, level) ->
                                    if (index > 0) append("  ")
                                    append("$metric ")
                                    withStyle(SpanStyle(color = colors.text)) { append(context.getString(level.label).lowercase(finnishLocale)) }
                                }
                            }
                        }
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    },
                    trailing = {
                        IconCircleButton(Icons.Outlined.Close, stringResource(R.string.remove_named, label), {
                            onChange(observations.filterNot { it.region == row.region })
                        }, enabled = !saving)
                    },
                    enabled = !saving,
                    onClick = { region = row.region }
                )
            }
        }
    }
    if (picker) {
        val maxHeight = LocalWindowInfo.current.containerDpSize.height * Dimens.bodyAreaSheetHeightFraction - Dimens.touch
        EditorSheet(
            stringResource(R.string.add_body_area), onDismiss = { picker = false },
            contentModifier = Modifier.heightIn(max = maxHeight.coerceAtLeast(Dimens.zero))
        ) {
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                itemsIndexed(bodyRegions, key = { _, area -> area.id }) { index, area ->
                    if (index > 0) HorizontalDivider(color = colors.divider)
                    ListRow(stringResource(bodyRegionLabel(area.id)), onClick = {
                        addArea(area.id)
                        picker = false
                    }, showChevron = false)
                }
            }
        }
    }
    if (clearAreas) {
        val count = observations.size
        ConfirmDialog(
            pluralStringResource(R.plurals.remove_areas_question, count, count),
            stringResource(R.string.remove_body_areas_message),
            stringResource(R.string.remove),
            destructive = true,
            onConfirm = {
                if (!saving) {
                    onChange(emptyList())
                    showBody = false
                }
                clearAreas = false
            },
            onDismiss = { clearAreas = false }
        )
    }
    observations.find { it.region == region }?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { region = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                Text(stringResource(bodyRegionLabel(row.region)), style = MaterialTheme.typography.titleLarge)
                fun replaceArea(updated: BodyObservation) {
                    if (!saving) onChange(observations.map { if (it.region == updated.region) updated else it })
                }
                for ((metric, value) in listOf(R.string.pain to row.pain, R.string.stiffness to row.stiffness)) {
                    val label = stringResource(metric)
                    val description = stringResource(R.string.body_area_metric, label, stringResource(bodyRegionLabel(row.region)))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    SegmentedControl(BodyLevel.entries.map { stringResource(it.label) }, BodyLevel.entries.indexOf(bodyLevelOf(value)), {
                        val level = BodyLevel.entries[it].stored
                        replaceArea(if (metric == R.string.pain) row.copy(pain = level) else row.copy(stiffness = level))
                    }, modifier = Modifier.semantics { contentDescription = description })
                }
                previous.find { it.region == row.region }?.let {
                    Text(stringResource(R.string.before), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    BodyLevelPills(it.pain, it.stiffness)
                }
                if (row.region in legacyNoteRegions) {
                    TextField(row.note, stringResource(R.string.body_area_note), { replaceArea(row.copy(note = it)) }, enabled = !saving)
                }
                PrimaryButton(stringResource(R.string.done), { region = null }, Modifier.fillMaxWidth())
            }
        }
    }
}

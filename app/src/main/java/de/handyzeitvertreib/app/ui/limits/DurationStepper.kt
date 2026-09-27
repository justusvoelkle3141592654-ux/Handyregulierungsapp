package de.handyzeitvertreib.app.ui.limits

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitRules
import de.handyzeitvertreib.app.ui.common.formatMinutes
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.TabularNumbers

private val PRESETS = listOf(15, 30, 45, 60, 90, 120)

/** 5-minute stepper with presets. Values outside the allowed range cannot be produced. */
@Composable
fun DurationStepper(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onChange(LimitRules.decrement(minutes)) },
                enabled = minutes > LimitRules.MIN_MINUTES,
                modifier = Modifier.testTag("duration-decrease"),
            ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.limit_editor_decrease)) }
            Text(
                formatMinutes(minutes),
                style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
                color = HzvTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("duration-value")
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
            FilledTonalIconButton(
                onClick = { onChange(LimitRules.increment(minutes)) },
                enabled = minutes < LimitRules.MAX_MINUTES,
                modifier = Modifier.testTag("duration-increase"),
            ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.limit_editor_increase)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { preset ->
                FilterChip(selected = minutes == preset, onClick = { onChange(preset) }, label = { Text(formatMinutes(preset)) })
            }
        }
    }
}

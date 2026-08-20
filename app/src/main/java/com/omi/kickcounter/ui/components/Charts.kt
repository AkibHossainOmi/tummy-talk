package com.omi.kickcounter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omi.kickcounter.ui.theme.LocalAccents

/**
 * Column chart with the count printed above every bar — the number is the point of
 * the chart, so it should never require reading a bar against an axis. Bars are
 * tappable; the caller renders whatever detail the selection deserves.
 */
@Composable
fun ColumnChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    showZeroValues: Boolean = true,
) {
    val accents = LocalAccents.current
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val fraction by animateFloatAsState(
                    targetValue = value.toFloat() / max,
                    animationSpec = tween(400),
                    label = "bar$index",
                )
                val selected = index == selectedIndex

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (onSelect != null) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(index) }
                            } else {
                                Modifier
                            },
                        ),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (value > 0 || showZeroValues) {
                        Text(
                            text = "$value",
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                selected -> MaterialTheme.colorScheme.primary
                                value == 0 -> MaterialTheme.colorScheme.outline
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Reserve the label row so bars still line up along the baseline.
                            .fillMaxHeight(fraction.coerceAtLeast(0.015f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (value == 0) {
                                    SolidColor(MaterialTheme.colorScheme.outlineVariant)
                                } else {
                                    accents.barGradient
                                },
                            )
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

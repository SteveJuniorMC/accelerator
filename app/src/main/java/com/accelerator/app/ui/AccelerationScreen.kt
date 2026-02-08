package com.accelerator.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accelerator.app.sensor.AccelState
import com.accelerator.app.sensor.RunPhase

@Composable
fun AccelerationScreen(
    state: AccelState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ACCELERATOR",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
        )

        when (state.phase) {
            RunPhase.IDLE -> IdleView(onStart)
            RunPhase.WAITING -> WaitingView(onStop)
            RunPhase.RUNNING -> RunningView(state, onStop)
            RunPhase.FINISHED -> FinishedView(state, onReset)
        }
    }
}

@Composable
private fun IdleView(onStart: () -> Unit) {
    Spacer(Modifier.weight(1f))

    Text(
        text = "Mount your phone securely\nand tap Start when ready",
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        lineHeight = 24.sp
    )

    Spacer(Modifier.weight(1f))

    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("START", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(32.dp))
}

@Composable
private fun WaitingView(onStop: () -> Unit) {
    Spacer(Modifier.weight(1f))

    Text(
        text = "WAITING FOR LAUNCH",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        letterSpacing = 2.sp
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = "Accelerate to begin timing",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    Spacer(Modifier.weight(1f))

    OutlinedButton(
        onClick = onStop,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("CANCEL", fontSize = 16.sp)
    }

    Spacer(Modifier.height(32.dp))
}

@Composable
private fun RunningView(state: AccelState, onStop: () -> Unit) {
    Spacer(Modifier.height(16.dp))

    // Big speed display
    Text(
        text = "%.0f".format(state.currentSpeedMph),
        fontSize = 96.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "MPH",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        letterSpacing = 2.sp
    )

    Spacer(Modifier.height(24.dp))

    // Timer
    Text(
        text = "%.2f".format(state.elapsedSeconds),
        fontSize = 48.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "seconds",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )

    Spacer(Modifier.height(24.dp))

    // Live metrics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MetricChip("%.0f km/h".format(state.currentSpeedKmh))
        MetricChip("%.0f m".format(state.distanceMeters))
    }

    Spacer(Modifier.height(16.dp))

    // Milestones as they're hit
    if (state.result.zeroTo60Mph != null) {
        MilestoneRow("0-60 mph", "%.2f s".format(state.result.zeroTo60Mph))
    }
    if (state.result.zeroTo100Kmh != null) {
        MilestoneRow("0-100 km/h", "%.2f s".format(state.result.zeroTo100Kmh))
    }
    if (state.result.quarterMileTime != null) {
        MilestoneRow("1/4 mile", "%.2f s".format(state.result.quarterMileTime))
    }

    Spacer(Modifier.weight(1f))

    OutlinedButton(
        onClick = onStop,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("STOP", fontSize = 16.sp)
    }

    Spacer(Modifier.height(32.dp))
}

@Composable
private fun FinishedView(state: AccelState, onReset: () -> Unit) {
    Spacer(Modifier.height(8.dp))

    Text(
        text = "RUN COMPLETE",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        letterSpacing = 2.sp
    )

    Spacer(Modifier.height(32.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResultRow("Total Time", "%.2f s".format(state.result.totalTimeSeconds))
            ResultRow("Top Speed", "%.1f mph".format(state.result.topSpeedMph))
            ResultRow("Distance", "%.0f m (%.2f mi)".format(
                state.result.totalDistanceMeters,
                state.result.totalDistanceMeters / 1609.344
            ))

            if (state.result.zeroTo60Mph != null || state.result.zeroTo100Kmh != null || state.result.quarterMileTime != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            }

            state.result.zeroTo60Mph?.let {
                ResultRow("0-60 mph", "%.2f s".format(it), highlight = true)
            }

            state.result.zeroTo100Kmh?.let {
                ResultRow("0-100 km/h", "%.2f s".format(it), highlight = true)
            }

            state.result.quarterMileTime?.let { time ->
                ResultRow(
                    "1/4 Mile",
                    "%.2f s @ %.1f mph".format(time, state.result.quarterMileSpeed ?: 0.0),
                    highlight = true
                )
            }
        }
    }

    Spacer(Modifier.weight(1f))

    Button(
        onClick = onReset,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("NEW RUN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(32.dp))
}

@Composable
private fun ResultRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = if (highlight) 20.sp else 16.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MilestoneRow(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MetricChip(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

package com.github.jaideepaher.slatebrowser.browser.compose

import com.github.jaideepaher.slatebrowser.BrowserUiEvent
import com.github.jaideepaher.slatebrowser.browser.BrowserPresenter
import com.github.jaideepaher.slatebrowser.focus.PauseRequest
import com.github.jaideepaher.slatebrowser.focus.SiteName
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.ceil

@Composable
fun PauseOverlay(request: PauseRequest, presenter: BrowserPresenter) {
    var remaining by remember(request) {
        mutableIntStateOf(ceil(request.remaining(Instant.now())).toInt())
    }
    LaunchedEffect(request) {
        while (true) {
            remaining = ceil(request.remaining(Instant.now())).toInt()
            if (remaining <= 0) break
            delay(250)
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(88.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = colors.outline, style = stroke)
            if (request.delaySeconds > 0) {
                drawArc(
                    color = colors.onSurface,
                    startAngle = -90f,
                    sweepAngle = 360f * (remaining / request.delaySeconds).toFloat(),
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        if (remaining > 0) {
            Text(
                "$remaining",
                color = colors.onSurfaceVariant,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            SiteName.display(request.site),
            color = colors.onSurfaceVariant,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 28.dp)
        )
        Text(
            "Do you really want this?",
            color = colors.onSurface,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            "Take a breath. It will still be there later.",
            color = colors.onSurfaceVariant,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Button(
                onClick = { presenter.onEvent(BrowserUiEvent.PauseGoBack) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.onSurface, contentColor = colors.surface)
            ) {
                Text("Go back")
            }
            TextButton(
                onClick = { presenter.onEvent(BrowserUiEvent.PauseContinue) },
                enabled = remaining <= 0,
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 8.dp)
            ) {
                Text(if (remaining > 0) "Continue in ${remaining}s" else "Continue")
            }
        }
    }
}

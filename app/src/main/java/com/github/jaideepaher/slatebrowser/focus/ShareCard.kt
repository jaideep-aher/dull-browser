package com.github.jaideepaher.slatebrowser.focus

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.io.File
import java.io.FileOutputStream

/**
 * A square card for sharing a week. Totals only, no site names.
 */
object ShareCard {

    fun line(week: WeekSummary, streak: Int): String {
        val parts = mutableListOf(
            "Dull blocked ${week.attempts} ${if (week.attempts == 1) "attempt" else "attempts"} this week"
        )
        if (streak > 0) parts += "$streak-day streak"
        if (week.minutesSaved > 0) parts += "${Stats.savedLabel(week.minutesSaved)} saved"
        return parts.joinToString(" · ")
    }

    fun shareIntent(context: Context, bitmap: Bitmap, week: WeekSummary, streak: Int): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "week.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, line(week, streak))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun renderBitmap(context: Context, content: @Composable () -> Unit, widthPx: Int = 1080): Bitmap {
        val view = ComposeView(context).apply {
            (context as? LifecycleOwner)?.let(::setViewTreeLifecycleOwner)
            (context as? ViewModelStoreOwner)?.let(::setViewTreeViewModelStoreOwner)
            (context as? SavedStateRegistryOwner)?.let(::setViewTreeSavedStateRegistryOwner)
            setContent { content() }
        }
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, widthPx)
        val bitmap = Bitmap.createBitmap(widthPx, widthPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
}

@Composable
fun ShareCardContent(week: WeekSummary, streak: Int, paper: Color, ink: Color, muted: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(paper)
            .padding(28.dp)
    ) {
        ClosedMark(ink)
        Spacer(Modifier.weight(1f))
        Text(
            text = "${week.attempts}",
            color = ink,
            fontSize = 64.sp,
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = if (week.attempts == 1) "attempt blocked this week" else "attempts blocked this week",
            color = muted,
            fontSize = 17.sp,
        )
        Row(modifier = Modifier.padding(top = 20.dp)) {
            if (streak > 0) {
                Column {
                    Text("$streak", color = ink, fontSize = 26.sp)
                    Text("day streak", color = muted, fontSize = 13.sp)
                }
            }
            if (week.minutesSaved > 0) {
                if (streak > 0) Spacer(Modifier.width(24.dp))
                Column {
                    Text(Stats.savedLabel(week.minutesSaved), color = ink, fontSize = 26.sp)
                    Text("saved", color = muted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Dull Browser", color = muted, fontSize = 13.sp)
    }
}

@Composable
fun ClosedMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(44.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = color, style = stroke, radius = size.minDimension / 2 - stroke.width)
        val y = size.height / 2
        drawLine(color, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), stroke.width)
    }
}

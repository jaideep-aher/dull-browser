package com.github.jaideepaher.slatebrowser.settings.screens

import com.github.jaideepaher.slatebrowser.adblock.siteblock.SiteBlocker
import com.github.jaideepaher.slatebrowser.compose.StatusBar
import com.github.jaideepaher.slatebrowser.focus.Bookmarks
import com.github.jaideepaher.slatebrowser.focus.Countdowns
import com.github.jaideepaher.slatebrowser.focus.CustomBlocklist
import com.github.jaideepaher.slatebrowser.focus.Feature
import com.github.jaideepaher.slatebrowser.focus.FocusCoordinator
import com.github.jaideepaher.slatebrowser.focus.PauseCategory
import com.github.jaideepaher.slatebrowser.focus.PauseList
import com.github.jaideepaher.slatebrowser.focus.ReadLater
import com.github.jaideepaher.slatebrowser.focus.ReadingWindow
import com.github.jaideepaher.slatebrowser.focus.ScheduledRemoval
import com.github.jaideepaher.slatebrowser.focus.ShareCard
import com.github.jaideepaher.slatebrowser.focus.ShareCardContent
import com.github.jaideepaher.slatebrowser.focus.SiteAddress
import com.github.jaideepaher.slatebrowser.focus.SiteName
import com.github.jaideepaher.slatebrowser.focus.Stats
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusScaffold(
    title: String,
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    onUp: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler { onUp() }
    Scaffold(
        topBar = {
            StatusBar(paintSurfaceColor = false, useBlackStatusBarStateFlow = useBlackStatusBarStateFlow)
            TopAppBar(title = { Text(title) })
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) { content() }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PauseSettingsScreen(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    pauseList: PauseList,
    siteBlocker: SiteBlocker,
    onUp: () -> Unit,
) {
    var settings by remember { mutableStateOf(pauseList.settings) }
    var newSite by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<Pair<ScheduledRemoval.Kind, String>?>(null) }
    LaunchedEffect(Unit) {
        pauseList.applyDueRemovals()
        settings = pauseList.settings
    }
    fun refresh() { settings = pauseList.settings }

    FocusScaffold("Pause before sites", useBlackStatusBarStateFlow, onUp) {
        SectionHeader("Categories")
        PauseCategory.entries.forEach { category ->
            PauseRow(
                title = category.title,
                detail = category.domains.take(4).joinToString(", ") + "…",
                kind = ScheduledRemoval.Kind.CATEGORY,
                value = category.rawValue,
                isOn = settings.categories.contains(category),
                pending = pauseList.pendingRemoval(ScheduledRemoval.Kind.CATEGORY, category.rawValue),
                onTurnOn = { pauseList.turnOn(category); refresh() },
                onTurnOff = { confirming = ScheduledRemoval.Kind.CATEGORY to category.rawValue },
                onKeep = { pending -> pauseList.cancelRemoval(pending); refresh() },
            )
        }
        Footer("Major news sites are already blocked and stay blocked. Pauses cover sites that are not.")

        SectionHeader("Your sites")
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSite,
                onValueChange = { newSite = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("example.com") }
            )
            TextButton(
                onClick = {
                    when (val result = pauseList.addSite(newSite) { siteBlocker.isListed(it) }) {
                        is PauseList.AddResult.Added -> {
                            message = "${result.domain} now pauses first."
                            newSite = ""
                        }
                        is PauseList.AddResult.AlreadyPaused -> {
                            message = "${result.domain} already pauses first."
                            newSite = ""
                        }
                        is PauseList.AddResult.Blocked -> {
                            message = "${result.domain} is blocked, which is stronger than a pause."
                            newSite = ""
                        }
                        PauseList.AddResult.Invalid -> message = "Enter a site name like example.com."
                    }
                    refresh()
                },
                enabled = newSite.isNotBlank()
            ) { Text("Add") }
        }
        message?.let { Footer(it) }
        settings.sites.forEach { site ->
            PauseRow(
                title = site,
                detail = null,
                kind = ScheduledRemoval.Kind.SITE,
                value = site,
                isOn = true,
                pending = pauseList.pendingRemoval(ScheduledRemoval.Kind.SITE, site),
                onTurnOn = {},
                onTurnOff = { confirming = ScheduledRemoval.Kind.SITE to site },
                onKeep = { pending -> pauseList.cancelRemoval(pending); refresh() },
            )
        }
        Footer("Turning a pause on happens now. Turning one off takes 24 hours, and you can change your mind before then. Blocked sites are never paused; they stay closed.")
    }

    confirming?.let { (kind, value) ->
        val name = if (kind == ScheduledRemoval.Kind.CATEGORY) {
            PauseCategory.entries.first { it.rawValue == value }.title
        } else value
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Turn this pause off?") },
            text = { Text("$name keeps its pause for 24 more hours. You can cancel before then.") },
            confirmButton = {
                TextButton(onClick = {
                    if (kind == ScheduledRemoval.Kind.CATEGORY) {
                        PauseCategory.entries.find { it.rawValue == value }?.let(pauseList::scheduleRemoval)
                    } else {
                        pauseList.scheduleRemovalOfSite(value)
                    }
                    confirming = null
                    refresh()
                }) { Text("Turn off in 24 hours") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PauseRow(
    title: String,
    detail: String?,
    kind: ScheduledRemoval.Kind,
    value: String,
    isOn: Boolean,
    pending: ScheduledRemoval?,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onKeep: (ScheduledRemoval) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            pending?.let {
                Text("Turns off in 24 hours", style = MaterialTheme.typography.bodySmall)
            }
        }
        when {
            !isOn -> TextButton(onClick = onTurnOn) { Text("Turn on") }
            pending != null -> TextButton(onClick = { onKeep(pending) }) { Text("Keep") }
            else -> TextButton(onClick = onTurnOff) { Text("Turn off…") }
        }
    }
}

@Composable
fun AddedSitesScreen(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    siteBlocker: SiteBlocker,
    customBlocklist: CustomBlocklist,
    onUp: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var sites by remember { mutableStateOf(customBlocklist.entries.value) }
    FocusScaffold("Sites you added", useBlackStatusBarStateFlow, onUp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("example.com") }
            )
            TextButton(
                onClick = {
                    val site = SiteAddress.normalize(entry)
                    if (site == null) {
                        message = "Enter a site name like example.com."
                    } else if (siteBlocker.isListed(site)) {
                        message = "$site is already blocked."
                        entry = ""
                    } else {
                        message = null
                        pending = site
                    }
                },
                enabled = entry.isNotBlank()
            ) { Text("Block") }
        }
        message?.let { Footer(it) }
        Footer("A site you add is blocked like the sites on the built-in list, including its subdomains. It cannot be removed later.")
        if (sites.isNotEmpty()) {
            SectionHeader("Blocked by you")
            sites.asReversed().forEach { Text(it, modifier = Modifier.padding(16.dp)) }
        }
    }
    pending?.let { site ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Block $site for good?") },
            text = { Text("There is no way to unblock a site you add.") },
            confirmButton = {
                TextButton(onClick = {
                    if (siteBlocker.addCustom(site) is CustomBlocklist.AddResult.Added) {
                        message = "$site is now blocked."
                        entry = ""
                        sites = customBlocklist.entries.value
                    }
                    pending = null
                }) { Text("Block for good") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CountdownsScreen(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    countdowns: Countdowns,
    onUp: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var upcoming by remember { mutableStateOf(countdowns.upcoming) }
    fun refresh() { upcoming = countdowns.upcoming }
    FocusScaffold("Countdowns", useBlackStatusBarStateFlow, onUp) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Name, like Finals") }
        )
        Text("Date: $date", modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(16.dp)) {
            TextButton(onClick = { date = date.minusDays(1).coerceAtLeast(LocalDate.now()) }) { Text("Earlier") }
            TextButton(onClick = { date = date.plusDays(1) }) { Text("Later") }
        }
        Button(
            onClick = {
                countdowns.add(name, date)
                name = ""
                refresh()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.padding(16.dp)
        ) { Text("Add countdown") }
        Footer("The start page shows the nearest date next to the clock. Dates that have passed are hidden.")
        if (upcoming.isNotEmpty()) {
            SectionHeader("Coming up")
            upcoming.forEach { (item, days) ->
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, modifier = Modifier.weight(1f))
                    Text(Countdowns.whenLabel(days))
                    TextButton(onClick = { countdowns.remove(item.id); refresh() }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
fun StatsSettingsScreen(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    stats: Stats,
    onUp: () -> Unit,
) {
    val context = LocalContext.current
    val today = stats.day(stats.today) ?: com.github.jaideepaher.slatebrowser.focus.DayStats(stats.today)
    val week = stats.week()
    val streak = stats.currentStreak
    var minutes by remember { mutableStateOf(stats.minutesPerAttempt) }
    val colors = MaterialTheme.colorScheme
    val card = remember(week, streak, minutes) {
        runCatching {
            ShareCard.renderBitmap(context, {
                ShareCardContent(week, streak, colors.surface, colors.onSurface, colors.onSurfaceVariant)
            })
        }.getOrNull()
    }
    FocusScaffold("Stats", useBlackStatusBarStateFlow, onUp) {
        if (Feature.WEEKLY_SHARE_CARD.isUnlocked) {
            card?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = ShareCard.line(week, streak),
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                )
                Button(
                    onClick = {
                        context.startActivity(Intent.createChooser(ShareCard.shareIntent(context, it, week, streak), "This week in Dull"))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("Share this week") }
            }
            Footer("The card shows totals only, never which sites you tried.")
        }
        SectionHeader("Today")
        StatRow("Blocked attempts", today.blockedTotal.toString())
        StatRow("Paused, then went back", today.wentBack.toString())
        StatRow("Paused, then continued", today.continued.toString())
        SectionHeader("Last 7 days")
        StatRow("Blocked attempts", week.attempts.toString())
        StatRow("Went back from a pause", week.wentBack.toString())
        StatRow("Time saved", Stats.savedLabel(week.minutesSaved))
        Text("Minutes per attempt", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Stats.MINUTE_OPTIONS.forEach { option ->
                TextButton(onClick = {
                    stats.minutesPerAttempt = option
                    minutes = option
                }) { Text(if (option == minutes) "· $option" else "$option") }
            }
        }
        Footer("Time saved counts each blocked attempt and each pause you went back from as $minutes minutes you did not spend there.")
        SectionHeader("Streak")
        StatRow("Streak", if (streak == 1) "1 day" else "$streak days")
        Footer("A day counts when you open Dull and no pause is turned off that day.")
        if (Feature.MILESTONES.isUnlocked) {
            SectionHeader("Milestones")
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            Stats.MILESTONE_DAYS.forEach { days ->
                val reached = stats.snapshot.milestones.firstOrNull { it.days == days }
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("$days days", modifier = Modifier.weight(1f), color = if (reached == null) colors.onSurfaceVariant else colors.onSurface)
                    reached?.let {
                        Text(LocalDate.parse(it.reached).format(format), color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (week.topSites.isNotEmpty()) {
            SectionHeader("Most tried this week")
            week.topSites.forEach { (site, count) ->
                StatRow(SiteName.display(site), count.toString())
            }
            Footer("Everything here stays on this phone.")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value)
    }
}

@Composable
fun ReadLaterSettingsScreen(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    readLater: ReadLater,
    onUp: () -> Unit,
) {
    var window by remember { mutableStateOf(readLater.window) }
    fun set(next: ReadingWindow) {
        window = next
        readLater.window = next
    }
    FocusScaffold("Read later", useBlackStatusBarStateFlow, onUp) {
        if (readLater.all.isEmpty()) {
            Footer("Save a page or a link for later from the page menu or by pressing on a link.")
        }
        if (!readLater.isOpenNow()) {
            Footer(ReadingWindow.waitLabel(readLater.timeUntilOpen()))
        }
        if (readLater.unread.isNotEmpty()) {
            SectionHeader("To read")
            readLater.unread.forEach {
                Text(it.title, modifier = Modifier.padding(16.dp))
            }
        }
        if (Feature.READING_WINDOW.isUnlocked) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Only open during reading time", modifier = Modifier.weight(1f))
                Switch(checked = window.enabled, onCheckedChange = { set(window.copy(enabled = it)) })
            }
            if (window.enabled) {
                Text("From ${ReadingWindow.label(window.start)}", modifier = Modifier.padding(horizontal = 16.dp))
                Slider(
                    value = window.start.toFloat(),
                    onValueChange = { set(window.copy(start = (it / 30).roundToInt() * 30)) },
                    valueRange = 0f..(24 * 60 - 30).toFloat(),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text("Until ${ReadingWindow.label(window.end)}", modifier = Modifier.padding(horizontal = 16.dp))
                Slider(
                    value = window.end.toFloat(),
                    onValueChange = { set(window.copy(end = (it / 30).roundToInt() * 30)) },
                    valueRange = 0f..(24 * 60).toFloat(),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Footer("Outside reading time, saved pages stay locked. Blocked and paused sites follow their usual rules when opened.")
        }
    }
}

@Composable
fun PasswordsScreen(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    onUp: () -> Unit,
) {
    val context = LocalContext.current
    FocusScaffold("Passwords", useBlackStatusBarStateFlow, onUp) {
        Footer("Saved passwords and passkeys stay in the Android Autofill service or your password manager. Dull never sees or stores them.")
        Button(
            onClick = { openAutofillSettings(context) },
            modifier = Modifier.padding(16.dp)
        ) { Text("Open Autofill settings") }
    }
}

fun openAutofillSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        },
        Intent("android.settings.AUTOFILL_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
    }
}

class FocusSettingsDependencies @Inject constructor(
    val pauseList: PauseList,
    val stats: Stats,
    val bookmarks: Bookmarks,
    val readLater: ReadLater,
    val countdowns: Countdowns,
    val focusCoordinator: FocusCoordinator,
    val siteBlocker: dagger.Lazy<SiteBlocker>,
)

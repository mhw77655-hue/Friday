package ai.friday.assistant.ui.navigation
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.WakingSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ai.friday.assistant.ui.screens.AlertsScreen
import ai.friday.assistant.ui.screens.ApprovalsScreen
import ai.friday.assistant.ui.screens.BootstrapScreen
import ai.friday.assistant.ui.screens.ConversationScreen
import ai.friday.assistant.ui.screens.EnvironmentScreen
import ai.friday.assistant.ui.screens.HomeScreen
import ai.friday.assistant.ui.screens.IndividualScreen
import ai.friday.assistant.ui.screens.MemoryScreen
import ai.friday.assistant.ui.screens.MissionsScreen
import ai.friday.assistant.ui.screens.ModelScreen
import ai.friday.assistant.ui.screens.ModelSimulationScreen
import ai.friday.assistant.ui.screens.MoreSheet
import ai.friday.assistant.ui.screens.SandboxScreen
import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.screens.SessionsScreen
import ai.friday.assistant.ui.screens.SystemScreen
import ai.friday.assistant.ui.screens.TaskInboxScreen
import ai.friday.assistant.ui.screens.ToolsScreen
import ai.friday.assistant.ui.screens.VisionScreen
import ai.friday.assistant.ui.screens.VideoKnowledgeScreen
import ai.friday.assistant.ui.screens.VoiceStudioScreen
import ai.friday.assistant.ui.screens.FridayTableScreen
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

private const val ROUTE_HOME = "home"
private const val ROUTE_CONVERSATION = "conversation"
private const val ROUTE_MISSIONS = "missions"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_TOOLS = "tools"
private const val ROUTE_SYSTEM = "system"
private const val ROUTE_SANDBOX = "sandbox"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TASK_INBOX = "task_inbox"
private const val ROUTE_APPROVALS = "approvals"
private const val ROUTE_ALERTS = "alerts"
private const val ROUTE_SESSIONS = "sessions"
private const val ROUTE_BOOTSTRAP = "bootstrap"
private const val ROUTE_ENVIRONMENT = "environment"
private const val ROUTE_MODEL = "model"
private const val ROUTE_MODEL_SIMULATION = "model_simulation"
private const val ROUTE_VISION = "vision"
private const val ROUTE_VIDEO_KNOWLEDGE = "video_knowledge"
private const val ROUTE_VOICE_STUDIO = "voice_studio"
private const val ROUTE_AI_TABLE = "ai_table"
private const val ROUTE_INDIVIDUAL = "individual"

/**
 * Living-UI pass: real screen transitions. Design spec §25/§6 rule out
 * parallax, bounce, and spring overshoot — so this is a plain crossfade
 * (fadeIn/fadeOut) with a tiny scale-in (0.98 -> 1.0), not a directional
 * slide. A directional slide implies "forward/back" spatial order, which
 * doesn't make sense for a flat bottom-tab structure where every
 * destination is a sibling, not a step in a sequence. 220ms in / 150ms
 * out matches the "never instant snap" rule from §25 without lingering.
 */
private val tabEnter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)
private val tabExit = fadeOut(tween(150))

/**
 * Flat, hairline-bordered bottom bar — no Material3 NavigationBar, no
 * pill selection indicator, no icons. Deliberately custom per the
 * design spec's rejection of default Material3 component shapes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridayNavHost(showSetup: Boolean = false, onSetupComplete: () -> Unit = {}) {
    if (showSetup) {
        ai.friday.assistant.ui.screens.FirstLaunchSetupScreen(onComplete = onSetupComplete)
        return
    }

    val navController = rememberNavController()
    var moreSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val readiness by EngineAccess.readinessState.collectAsStateWithLifecycle()
    if (readiness !is ai.friday.assistant.EngineReadinessState.Ready) {
        WakingSurface(readiness)
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    /**
     * Fix: tab taps previously called plain navController.navigate(route),
     * which pushes a brand-new backstack entry (and a brand-new
     * ViewModelStore) every single time -- even when re-visiting a tab
     * you'd already been on. That silently orphaned the previous entry
     * and was the actual reason ConversationViewModel (and its message
     * history) got recreated from scratch on every tab switch. This is
     * the standard bottom-nav pattern: popUpTo the graph's start
     * destination with saveState, launchSingleTop so re-tapping the
     * same tab doesn't stack duplicates, and restoreState so returning
     * to a tab reuses its saved entry (and ViewModelStore) instead of
     * making a new one.
     */
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = FridayColors.SurfaceBase,
        bottomBar = {
            FridayBottomBar(
                currentRoute = currentRoute,
                moreSelected = moreSheetOpen,
                onHome = { navigateToTab(ROUTE_HOME) },
                onConversation = { navigateToTab(ROUTE_CONVERSATION) },
                onMissions = { navigateToTab(ROUTE_MISSIONS) },
                onMore = { moreSheetOpen = true }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { tabEnter },
            exitTransition = { tabExit },
            popEnterTransition = { tabEnter },
            popExitTransition = { tabExit }
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(onOpenTools = { navigateToTab(ROUTE_TOOLS) })
            }
            composable(ROUTE_CONVERSATION) { ConversationScreen() }
            composable(ROUTE_MISSIONS) { MissionsScreen() }
            composable(ROUTE_MEMORY) { MemoryScreen() }
            composable(ROUTE_TOOLS) { ToolsScreen() }
            composable(ROUTE_SYSTEM) { SystemScreen() }
            composable(ROUTE_SANDBOX) { SandboxScreen() }
            composable(ROUTE_SETTINGS) { SettingsScreen() }
            composable(ROUTE_TASK_INBOX) {
                val slot = EngineAccess.taskInbox()
                val manager = slot.value
                if (manager == null) WakingSurface(slot.readiness)
                else TaskInboxScreen(taskInbox = manager, onNavigate = ::navigateToTab)
            }
            composable(ROUTE_APPROVALS) {
                val slot = EngineAccess.approvalQueue()
                val queue = slot.value
                if (queue == null) WakingSurface(slot.readiness)
                else ApprovalsScreen(approvalQueue = queue, onNavigate = ::navigateToTab)
            }
            composable(ROUTE_ALERTS) {
                val slot = EngineAccess.alertStore()
                val store = slot.value
                if (store == null) WakingSurface(slot.readiness)
                else AlertsScreen(alertStore = store, onNavigate = ::navigateToTab)
            }
            composable(ROUTE_SESSIONS) {
                val slot = EngineAccess.sessionManager()
                val manager = slot.value
                if (manager == null) WakingSurface(slot.readiness)
                else SessionsScreen(sessionManager = manager, onNavigate = ::navigateToTab)
            }
            composable(ROUTE_ENVIRONMENT) {
                val slot = EngineAccess.environmentManager()
                val manager = slot.value
                if (manager == null) WakingSurface(slot.readiness)
                else EnvironmentScreen(environmentManager = manager, onNavigate = ::navigateToTab)
            }
            composable(ROUTE_MODEL) {
                val slot = EngineAccess.modelManager()
                val manager = slot.value
                if (manager == null) WakingSurface(slot.readiness)
                else ModelScreen(modelManager = manager, onNavigate = ::navigateToTab)
            }
            composable(ROUTE_MODEL_SIMULATION) { ModelSimulationScreen() }
            composable(ROUTE_VISION) { VisionScreen() }
            composable(ROUTE_VIDEO_KNOWLEDGE) { VideoKnowledgeScreen() }
            composable(ROUTE_VOICE_STUDIO) { VoiceStudioScreen() }
            composable(ROUTE_AI_TABLE) { FridayTableScreen() }
            composable(ROUTE_INDIVIDUAL) { IndividualScreen() }
            composable(ROUTE_BOOTSTRAP) {
                val slot = EngineAccess.bootstrapManager()
                val manager = slot.value
                if (manager == null) WakingSurface(slot.readiness)
                else BootstrapScreen(bootstrapManager = manager, onComplete = { navigateToTab(ROUTE_HOME) })
            }
        }

        if (moreSheetOpen) {
            MoreSheet(
                sheetState = sheetState,
                onDismiss = { moreSheetOpen = false },
                onNavigate = { route ->
                    moreSheetOpen = false
                    navigateToTab(route)
                }
            )
        }
    }
}

@Composable
private fun FridayBottomBar(
    currentRoute: String?,
    moreSelected: Boolean,
    onHome: () -> Unit,
    onConversation: () -> Unit,
    onMissions: () -> Unit,
    onMore: () -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BottomBarItem("home", "Home", currentRoute == ROUTE_HOME, onHome)
            BottomBarItem("talk", "Talk", currentRoute == ROUTE_CONVERSATION, onConversation)
            BottomBarItem("build", "Build", currentRoute == ROUTE_MISSIONS, onMissions)
            BottomBarItem("more", "More", moreSelected, onMore)
        }
    }
}

@Composable
private fun BottomBarItem(kind: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) FridayColors.CoreIdle else FridayColors.CoreOffline
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NavigationGlyph(kind, tint)
        Text(
            text = label,
            style = FridayType.Technical.copy(color = tint),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** 1px-equivalent monochrome line icons, drawn on the system's 24px grid. */
@Composable
private fun NavigationGlyph(kind: String, tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        val path = Path()
        when (kind) {
            "home" -> {
                path.moveTo(w * 0.12f, h * 0.48f)
                path.lineTo(w * 0.5f, h * 0.16f)
                path.lineTo(w * 0.88f, h * 0.48f)
                path.moveTo(w * 0.25f, h * 0.42f)
                path.lineTo(w * 0.25f, h * 0.86f)
                path.lineTo(w * 0.75f, h * 0.86f)
                path.lineTo(w * 0.75f, h * 0.42f)
            }
            "talk" -> {
                path.moveTo(w * 0.14f, h * 0.18f)
                path.lineTo(w * 0.86f, h * 0.18f)
                path.lineTo(w * 0.86f, h * 0.72f)
                path.lineTo(w * 0.48f, h * 0.72f)
                path.lineTo(w * 0.28f, h * 0.9f)
                path.lineTo(w * 0.3f, h * 0.72f)
                path.lineTo(w * 0.14f, h * 0.72f)
                path.close()
            }
            "build" -> {
                path.moveTo(w * 0.5f, h * 0.1f)
                path.lineTo(w * 0.9f, h * 0.5f)
                path.lineTo(w * 0.5f, h * 0.9f)
                path.lineTo(w * 0.1f, h * 0.5f)
                path.close()
                drawLine(tint, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.7f), stroke)
                drawLine(tint, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.7f, h * 0.5f), stroke)
            }
            else -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), stroke)
                drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), stroke)
                drawLine(tint, Offset(w * 0.18f, h * 0.72f), Offset(w * 0.82f, h * 0.72f), stroke)
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

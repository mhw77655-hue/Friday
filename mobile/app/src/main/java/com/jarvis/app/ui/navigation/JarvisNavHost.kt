package com.jarvis.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.jarvis.app.ui.screens.AlertsScreen
import com.jarvis.app.ui.screens.ApprovalsScreen
import com.jarvis.app.ui.screens.BootstrapScreen
import com.jarvis.app.ui.screens.ConversationScreen
import com.jarvis.app.ui.screens.EnvironmentScreen
import com.jarvis.app.ui.screens.HomeScreen
import com.jarvis.app.ui.screens.MemoryScreen
import com.jarvis.app.ui.screens.MissionsScreen
import com.jarvis.app.ui.screens.ModelScreen
import com.jarvis.app.ui.screens.MoreSheet
import com.jarvis.app.ui.screens.SandboxScreen
import com.jarvis.app.ui.screens.SettingsScreen
import com.jarvis.app.ui.screens.SessionsScreen
import com.jarvis.app.ui.screens.SystemScreen
import com.jarvis.app.ui.screens.TaskInboxScreen
import com.jarvis.app.ui.screens.ToolsScreen
import com.jarvis.app.ui.screens.getAlertStore
import com.jarvis.app.ui.screens.getApprovalQueue
import com.jarvis.app.ui.screens.getBootstrapManager
import com.jarvis.app.ui.screens.getEnvironmentManager
import com.jarvis.app.ui.screens.getModelManager
import com.jarvis.app.ui.screens.getSessionManager
import com.jarvis.app.ui.screens.getTaskInbox
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

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
fun JarvisNavHost() {
    val navController = rememberNavController()
    var moreSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
        containerColor = JarvisColors.SurfaceBase,
        bottomBar = {
            JarvisBottomBar(
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
            composable(ROUTE_TASK_INBOX) { TaskInboxScreen(taskInbox = getTaskInbox(), onNavigate = ::navigateToTab) }
            composable(ROUTE_APPROVALS) { ApprovalsScreen(approvalQueue = getApprovalQueue(), onNavigate = ::navigateToTab) }
            composable(ROUTE_ALERTS) { AlertsScreen(alertStore = getAlertStore(), onNavigate = ::navigateToTab) }
            composable(ROUTE_SESSIONS) { SessionsScreen(sessionManager = getSessionManager(), onNavigate = ::navigateToTab) }
            composable(ROUTE_ENVIRONMENT) { EnvironmentScreen(environmentManager = getEnvironmentManager(), onNavigate = ::navigateToTab) }
            composable(ROUTE_MODEL) { ModelScreen(modelManager = getModelManager(), onNavigate = ::navigateToTab) }
            composable(ROUTE_BOOTSTRAP) { BootstrapScreen(bootstrapManager = getBootstrapManager(), onComplete = { navigateToTab(ROUTE_HOME) }) }
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
private fun JarvisBottomBar(
    currentRoute: String?,
    moreSelected: Boolean,
    onHome: () -> Unit,
    onConversation: () -> Unit,
    onMissions: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisColors.SurfacePanel)
            .border(1.dp, JarvisColors.SurfaceHairline)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomBarItem("Home", currentRoute == ROUTE_HOME, onHome)
        BottomBarItem("Chat", currentRoute == ROUTE_CONVERSATION, onConversation)
        BottomBarItem("Missions", currentRoute == ROUTE_MISSIONS, onMissions)
        BottomBarItem("More", moreSelected, onMore)
    }
}

@Composable
private fun BottomBarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = if (selected) {
            JarvisType.Technical.copy(color = JarvisColors.TextPrimary)
        } else {
            JarvisType.Technical
        },
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
}

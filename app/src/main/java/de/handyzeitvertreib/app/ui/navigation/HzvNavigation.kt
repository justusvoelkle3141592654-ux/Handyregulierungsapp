package de.handyzeitvertreib.app.ui.navigation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.regulation.RegulationActivity
import de.handyzeitvertreib.app.ui.apps.AppDetailRoute
import de.handyzeitvertreib.app.ui.apps.AppDetailViewModel
import de.handyzeitvertreib.app.ui.apps.AppsRoute
import de.handyzeitvertreib.app.ui.apps.AppsViewModel
import de.handyzeitvertreib.app.ui.common.containerViewModel
import de.handyzeitvertreib.app.ui.dashboard.DashboardActions
import de.handyzeitvertreib.app.ui.dashboard.DashboardRoute
import de.handyzeitvertreib.app.ui.dashboard.DashboardScreen
import de.handyzeitvertreib.app.ui.dashboard.DashboardViewModel
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.insights.InsightsRoute
import de.handyzeitvertreib.app.ui.insights.InsightsViewModel
import de.handyzeitvertreib.app.ui.limits.AppLimitEditorRoute
import de.handyzeitvertreib.app.ui.limits.AppLimitEditorViewModel
import de.handyzeitvertreib.app.ui.limits.GroupEditorRoute
import de.handyzeitvertreib.app.ui.limits.GroupEditorViewModel
import de.handyzeitvertreib.app.ui.limits.LimitsActions
import de.handyzeitvertreib.app.ui.limits.LimitsRoute
import de.handyzeitvertreib.app.ui.limits.LimitsViewModel
import de.handyzeitvertreib.app.ui.settings.AccessibilitySettingsScreen
import de.handyzeitvertreib.app.ui.settings.AccountScreen
import de.handyzeitvertreib.app.ui.settings.DataManagementScreen
import de.handyzeitvertreib.app.ui.settings.ExtensionSettingsScreen
import de.handyzeitvertreib.app.ui.settings.HelpScreen
import de.handyzeitvertreib.app.ui.settings.PermissionsSettingsScreen
import de.handyzeitvertreib.app.ui.settings.PrivacyScreen
import de.handyzeitvertreib.app.ui.settings.SettingsDestination
import de.handyzeitvertreib.app.ui.settings.SettingsRoute
import de.handyzeitvertreib.app.ui.settings.SettingsViewModel
import de.handyzeitvertreib.app.ui.settings.UsageAccessSettingsScreen

enum class TopLevel(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    TODAY("today", R.string.nav_today, Icons.Outlined.WbSunny),
    APPS("apps", R.string.nav_apps, Icons.Outlined.Apps),
    LIMITS("limits", R.string.nav_limits, Icons.Outlined.Timer),
    INSIGHTS("insights", R.string.nav_insights, Icons.Outlined.Insights),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

private object Routes {
    const val APP_DETAIL = "app/{pkg}"
    const val APP_LIMIT = "limit/app/{pkg}"
    const val GROUP_LIMIT = "limit/group/{id}"
    const val SETTINGS_PAGE = "settings/{page}"

    fun appDetail(pkg: String) = "app/$pkg"

    fun appLimit(pkg: String) = "limit/app/$pkg"

    fun groupLimit(id: Long) = "limit/group/$id"

    fun settingsPage(page: SettingsDestination) = "settings/${page.name}"
}

private fun titleFor(
    route: String?,
    page: String?,
): Int? =
    when (route) {
        Routes.APP_DETAIL -> R.string.title_app_detail
        Routes.APP_LIMIT -> R.string.title_app_limit
        Routes.GROUP_LIMIT -> R.string.title_group_limit
        Routes.SETTINGS_PAGE ->
            when (page?.let { runCatching { SettingsDestination.valueOf(it) }.getOrNull() }) {
                SettingsDestination.PERMISSIONS -> R.string.settings_permissions
                SettingsDestination.ACCOUNT -> R.string.settings_account
                SettingsDestination.PRIVACY -> R.string.settings_privacy
                SettingsDestination.EXTENSION -> R.string.settings_extension
                SettingsDestination.DATA -> R.string.settings_data
                SettingsDestination.HELP -> R.string.settings_help
                SettingsDestination.USAGE_ACCESS -> R.string.usage_access_title
                SettingsDestination.ACCESSIBILITY -> R.string.accessibility_title
                null -> null
            }
        else -> null
    }

private fun NavHostController.navigateTopLevel(destination: TopLevel) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HzvMainScaffold(
    startDestination: TopLevel = TopLevel.TODAY,
    onDataReset: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = TopLevel.entries.firstOrNull { it.route == route }
    val context = LocalContext.current
    val colors = HzvTheme.colors
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (topLevel == null && route != null) {
                val title = titleFor(route, backStack?.arguments?.getString("page"))
                CenterAlignedTopAppBar(
                    title = { if (title != null) Text(stringResource(title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
        bottomBar = {
            if (topLevel != null) {
                NavigationBar(containerColor = colors.glassFillStrong) {
                    TopLevel.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == topLevel,
                            onClick = { navController.navigateTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = colors.accentSoft),
                        )
                    }
                }
            }
        },
    ) { padding ->
        HzvNavHost(navController, startDestination, padding, context, onDataReset)
    }
}

@Composable
private fun HzvNavHost(
    navController: NavHostController,
    startDestination: TopLevel,
    padding: PaddingValues,
    context: Context,
    onDataReset: () -> Unit,
) {
    val openRegulation: (LimitKey, String?) -> Unit = { key, pkg -> context.startActivity(RegulationActivity.intent(context, key, pkg)) }
    NavHost(navController, startDestination = startDestination.route) {
        composable(TopLevel.TODAY.route) {
            val viewModel = containerViewModel { DashboardViewModel(it) }
            DashboardRoute(
                viewModel,
                DashboardActions(
                    onSetUpUsageAccess = { navController.navigate(Routes.settingsPage(SettingsDestination.USAGE_ACCESS)) },
                    onOpenApp = { navController.navigate(Routes.appDetail(it)) },
                    onOpenLimits = { navController.navigateTopLevel(TopLevel.LIMITS) },
                    onOpenApps = { navController.navigateTopLevel(TopLevel.APPS) },
                    onOpenRegulation = openRegulation,
                    onSetUpEnforcement = { navController.navigate(Routes.settingsPage(SettingsDestination.PERMISSIONS)) },
                ),
                padding,
            )
        }
        composable(TopLevel.APPS.route) {
            AppsRoute(containerViewModel { AppsViewModel(it) }, onOpenApp = { navController.navigate(Routes.appDetail(it)) }, padding)
        }
        composable(TopLevel.LIMITS.route) {
            LimitsRoute(
                containerViewModel { LimitsViewModel(it) },
                LimitsActions(
                    onAddAppLimit = { navController.navigateTopLevel(TopLevel.APPS) },
                    onAddGroup = { navController.navigate(Routes.groupLimit(0)) },
                    onEditAppLimit = { navController.navigate(Routes.appLimit(it)) },
                    onEditGroup = { navController.navigate(Routes.groupLimit(it)) },
                ),
                padding,
            )
        }
        composable(TopLevel.INSIGHTS.route) {
            InsightsRoute(containerViewModel { InsightsViewModel(it) }, onOpenApp = { navController.navigate(Routes.appDetail(it)) }, padding)
        }
        composable(TopLevel.SETTINGS.route) {
            SettingsRoute(containerViewModel { SettingsViewModel(it) }, onNavigate = { navController.navigate(Routes.settingsPage(it)) }, padding)
        }
        composable(Routes.APP_DETAIL, arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { entry ->
            val pkg = entry.arguments?.getString("pkg").orEmpty()
            AppDetailRoute(
                containerViewModel(key = "detail-$pkg") { AppDetailViewModel(it, pkg) },
                onEditLimit = { navController.navigate(Routes.appLimit(it)) },
                onOpenExtensionSettings = { navController.navigate(Routes.settingsPage(SettingsDestination.EXTENSION)) },
                contentPadding = padding,
            )
        }
        composable(Routes.APP_LIMIT, arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { entry ->
            val pkg = entry.arguments?.getString("pkg").orEmpty()
            AppLimitEditorRoute(containerViewModel(key = "limit-$pkg") { AppLimitEditorViewModel(it, pkg) }, onDone = { navController.navigateUp() }, padding)
        }
        composable(Routes.GROUP_LIMIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            GroupEditorRoute(containerViewModel(key = "group-$id") { GroupEditorViewModel(it, id) }, onDone = { navController.navigateUp() }, padding)
        }
        composable(Routes.SETTINGS_PAGE, arguments = listOf(navArgument("page") { type = NavType.StringType })) { entry ->
            val page = entry.arguments?.getString("page")?.let { runCatching { SettingsDestination.valueOf(it) }.getOrNull() }
            val viewModel = containerViewModel { SettingsViewModel(it) }
            when (page) {
                SettingsDestination.PERMISSIONS -> PermissionsSettingsScreen(viewModel, { navController.navigate(Routes.settingsPage(it)) }, padding)
                SettingsDestination.ACCOUNT -> AccountScreen(viewModel, padding)
                SettingsDestination.PRIVACY -> PrivacyScreen(viewModel, { navController.navigate(Routes.settingsPage(SettingsDestination.DATA)) }, padding)
                SettingsDestination.EXTENSION -> ExtensionSettingsScreen(viewModel, { navController.navigateUp() }, padding)
                SettingsDestination.DATA -> DataManagementScreen(viewModel, onDataReset, padding)
                SettingsDestination.HELP -> HelpScreen(padding)
                SettingsDestination.USAGE_ACCESS -> UsageAccessSettingsScreen(viewModel, padding)
                SettingsDestination.ACCESSIBILITY -> AccessibilitySettingsScreen(viewModel, padding)
                null -> Unit
            }
        }
    }
}

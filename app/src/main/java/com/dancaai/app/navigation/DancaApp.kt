package com.dancaai.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dancaai.app.ui.components.BottomNav
import com.dancaai.app.ui.components.DcaTab
import com.dancaai.app.ui.screens.HistoryScreen
import com.dancaai.app.ui.screens.HomeScreen
import com.dancaai.app.ui.screens.OnboardingScreen
import com.dancaai.app.ui.screens.PlaceholderScreen
import com.dancaai.app.ui.screens.ProfileScreen
import com.dancaai.app.ui.screens.ResultsScreen
import com.dancaai.app.ui.screens.SessionScreen
import com.dancaai.app.ui.theme.DancaAITheme
import com.dancaai.app.ui.theme.DcaTheme

/** Raiz da UI em Compose: tema + Scaffold com bottom nav + grafo de navegação. */
@Composable
fun DancaApp() {
    DancaAITheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = currentRoute in Routes.topLevel

        Scaffold(
            containerColor = DcaTheme.colors.bg,
            bottomBar = {
                if (showBottomBar) {
                    BottomNav(
                        selected = tabForRoute(currentRoute),
                        onSelect = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.ONBOARDING,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onFinish = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onStartTraining = { navController.navigate(Routes.SESSION) },
                        onSeeHistory = {
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenProfile = {
                            navController.navigate(Routes.PROFILE) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Routes.SESSION) {
                    SessionScreen(
                        onBack = { navController.popBackStack() },
                        onStart = { navController.navigate(Routes.TRAINING) },
                    )
                }
                composable(Routes.TRAINING) {
                    PlaceholderScreen(
                        title = "Treino",
                        primaryLabel = "Encerrar sessão",
                        onPrimary = {
                            navController.navigate(Routes.RESULTS) {
                                popUpTo(Routes.HOME)
                            }
                        },
                    )
                }
                composable(Routes.RESULTS) {
                    ResultsScreen(
                        onAgain = {
                            navController.navigate(Routes.SESSION) { popUpTo(Routes.HOME) }
                        },
                        onHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.HISTORY) { HistoryScreen() }
                composable(Routes.PROFILE) { ProfileScreen() }
            }
        }
    }
}

/** Mapeia a rota atual para a aba correspondente da bottom nav. */
private fun tabForRoute(route: String?): DcaTab = when (route) {
    Routes.HISTORY -> DcaTab.History
    Routes.PROFILE -> DcaTab.Profile
    else -> DcaTab.Home
}

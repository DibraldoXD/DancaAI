package com.dancaai.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.dancaai.app.ui.RootViewModel
import com.dancaai.app.ui.components.BottomNav
import com.dancaai.app.ui.components.DcaTab
import com.dancaai.app.ui.screens.HistoryScreen
import com.dancaai.app.ui.screens.HomeScreen
import com.dancaai.app.ui.screens.OnboardingScreen
import com.dancaai.app.ui.screens.ProfileScreen
import com.dancaai.app.ui.screens.ResultsScreen
import com.dancaai.app.ui.screens.SessionScreen
import com.dancaai.app.ui.screens.TrainingScreen
import com.dancaai.app.ui.session.SessionFlowViewModel
import com.dancaai.app.ui.theme.DancaAITheme
import com.dancaai.app.ui.theme.DcaTheme

/** Raiz da UI em Compose: tema + Scaffold com bottom nav + grafo de navegação. */
@Composable
fun DancaApp() {
    DancaAITheme {
        val rootViewModel: RootViewModel = viewModel()
        val settings by rootViewModel.settings.collectAsStateWithLifecycle()

        when (val user = settings) {
            // A primeira leitura do DataStore ainda não chegou. Um fundo neutro evita
            // montar o NavHost na rota errada e corrigi-la com um salto visível.
            null -> Box(Modifier.fillMaxSize().background(DcaTheme.colors.bg))

            else -> DancaNavigation(
                startDestination = if (user.onboardingDone) Routes.HOME else Routes.ONBOARDING,
                onOnboardingFinish = rootViewModel::completeOnboarding,
            )
        }
    }
}

@Composable
private fun DancaNavigation(
    startDestination: String,
    onOnboardingFinish: (name: String, levelId: String) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel

    /** Navega para uma aba de nível superior preservando o estado das demais. */
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = DcaTheme.colors.bg,
        bottomBar = {
            if (showBottomBar) {
                BottomNav(
                    selected = tabForRoute(currentRoute),
                    onSelect = { tab -> navigateToTab(tab.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 6 } },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = { name, levelId ->
                        onOnboardingFinish(name, levelId)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onStartTraining = { navController.navigate(Routes.SESSION_FLOW) },
                    onSeeHistory = { navigateToTab(Routes.HISTORY) },
                    onOpenProfile = { navigateToTab(Routes.PROFILE) },
                )
            }

            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }

            sessionFlow(navController)
        }
    }
}

/**
 * Nova sessão → Treino → Resultado, sob um subgrafo próprio. As três telas
 * compartilham o [SessionFlowViewModel] escopado a ele, que transporta a
 * configuração escolhida e o id da sessão gravada.
 */
private fun NavGraphBuilder.sessionFlow(navController: NavHostController) {
    navigation(startDestination = Routes.SESSION, route = Routes.SESSION_FLOW) {
        composable(Routes.SESSION) { entry ->
            val viewModel = entry.sessionFlowViewModel(navController)
            SessionScreen(
                config = viewModel.config,
                onConfigChange = viewModel::updateConfig,
                onBack = { navController.popBackStack() },
                onStart = {
                    viewModel.startSession()
                    navController.navigate(Routes.TRAINING)
                },
            )
        }

        composable(Routes.TRAINING) { entry ->
            val viewModel = entry.sessionFlowViewModel(navController)
            TrainingScreen(
                config = viewModel.config,
                onConfigChange = viewModel::updateConfig,
                onEnd = { elapsedSec, metrics ->
                    viewModel.finishSession(elapsedSec, metrics)
                    navController.navigate(Routes.RESULTS) {
                        popUpTo(Routes.TRAINING) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.RESULTS) { entry ->
            val viewModel = entry.sessionFlowViewModel(navController)
            ResultsScreen(
                outcome = viewModel.outcome,
                onAgain = {
                    navController.navigate(Routes.SESSION) {
                        popUpTo(Routes.SESSION_FLOW) { inclusive = true }
                    }
                },
                onHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SESSION_FLOW) { inclusive = true }
                    }
                },
            )
        }
    }
}

/**
 * ViewModel escopado ao subgrafo da sessão, e não à tela: é o que faz a
 * configuração sobreviver à navegação entre Nova sessão, Treino e Resultado.
 */
@Composable
private fun NavBackStackEntry.sessionFlowViewModel(
    navController: NavHostController,
): SessionFlowViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(Routes.SESSION_FLOW) }
    return viewModel(parentEntry)
}

/** Mapeia a rota atual para a aba correspondente da bottom nav. */
private fun tabForRoute(route: String?): DcaTab = when (route) {
    Routes.HISTORY -> DcaTab.History
    Routes.PROFILE -> DcaTab.Profile
    else -> DcaTab.Home
}

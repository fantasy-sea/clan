package com.fantaysea.clan.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fantaysea.clan.LocalAppPreferences
import com.fantaysea.clan.ui.onboarding.OnboardingScreen
import com.fantaysea.clan.ui.onboarding.onboardingPages
import kotlinx.coroutines.launch


@Composable
fun AppNavigation (){
    val navController = rememberNavController()
    val appPreferences =LocalAppPreferences.current
    val coroutineScope = rememberCoroutineScope()
    val isSkillOnboarding =appPreferences.onboardingCompleted.collectAsState(initial = true)
    NavHost(
        navController =navController,
        startDestination = if (isSkillOnboarding.value) HomeRoute.GRAPH else "onboarding"
    ){
        homeGraph(navController)
        composable("onboarding"){
            OnboardingScreen(
                pages = remember { onboardingPages() },
                onComplete = { coroutineScope.launch { appPreferences.setOnboardingCompleted(true) } },
            )
        }
    }


}
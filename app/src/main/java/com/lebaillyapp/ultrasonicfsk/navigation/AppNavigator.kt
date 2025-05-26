package com.lebaillyapp.ultrasonicfsk.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lebaillyapp.ultrasonicfsk.screen.ReceiverScreen
import com.lebaillyapp.ultrasonicfsk.screen.SenderScreen
import com.lebaillyapp.ultrasonicfsk.screen.ToneTestScreen

@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "toneTest") {
        composable("sender") { SenderScreen(navController) }
        composable("receiver") { ReceiverScreen(navController) }
        composable("toneTest") { ToneTestScreen(navController) }
    }
}
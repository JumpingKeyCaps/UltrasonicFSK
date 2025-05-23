package com.lebaillyapp.ultrasonicfsk.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ReceiverScreen(navController: NavController) {
    val receivedMessage = remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Réception en cours…", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        Text(receivedMessage.value)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { navController.navigate("sender") }) {
            Text("Retour à l’émetteur")
        }
    }
}
package com.lebaillyapp.ultrasonicfsk.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun SenderScreen(navController: NavController) {
    var message by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Message à envoyer")
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* TODO: lancer l'émission */ }) {
            Text("Envoyer")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { navController.navigate("receiver") }) {
            Text("Aller au récepteur")
        }
    }
}
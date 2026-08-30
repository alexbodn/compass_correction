package com.example.compasscorrector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.compasscorrector.TestLocationConfig

@Composable
fun TestLocationDialog(onDismiss: () -> Unit) {
    var networkAvailable by remember { mutableStateOf(TestLocationConfig.networkAvailable) }
    var networkLat by remember { mutableStateOf(TestLocationConfig.networkLat) }
    var networkLon by remember { mutableStateOf(TestLocationConfig.networkLon) }

    var gnssInFix by remember { mutableStateOf(TestLocationConfig.gnssInFix) }
    var gnssLat by remember { mutableStateOf(TestLocationConfig.gnssLat) }
    var gnssLon by remember { mutableStateOf(TestLocationConfig.gnssLon) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test Location Overrides", style = MaterialTheme.typography.titleMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Method", modifier = Modifier.weight(1f))
                    Text("Avail/Fix", modifier = Modifier.weight(1f))
                    Text("Lat", modifier = Modifier.weight(1f))
                    Text("Lon", modifier = Modifier.weight(1f))
                }

                Divider()

                // Network Row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Network", modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = networkAvailable,
                        onCheckedChange = { networkAvailable = it },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = networkLat,
                        onValueChange = { networkLat = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = networkLon,
                        onValueChange = { networkLon = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // GNSS Row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("GNSS", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = gnssInFix,
                        onValueChange = { gnssInFix = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gnssLat,
                        onValueChange = { gnssLat = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gnssLon,
                        onValueChange = { gnssLon = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        TestLocationConfig.networkAvailable = networkAvailable
                        TestLocationConfig.networkLat = networkLat
                        TestLocationConfig.networkLon = networkLon
                        TestLocationConfig.gnssInFix = gnssInFix
                        TestLocationConfig.gnssLat = gnssLat
                        TestLocationConfig.gnssLon = gnssLon
                        TestLocationConfig.isTestingMode = true
                        onDismiss()
                    }) {
                        Text("Submit & Enable Test Mode")
                    }
                }
            }
        }
    }
}

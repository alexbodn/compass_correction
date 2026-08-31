package com.example.compasscorrector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compasscorrector.TestLocationConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun SpoofScreen(
    foregroundColor: Color,
    backgroundColor: Color
) {
    var networkSpoofEnabled by remember { mutableStateOf(TestLocationConfig.networkSpoofEnabled) }
    var networkAvailable by remember { mutableStateOf(TestLocationConfig.networkAvailable) }
    var networkSpoofCoords by remember { mutableStateOf(TestLocationConfig.networkSpoofCoords) }

    var gnssSpoofEnabled by remember { mutableStateOf(TestLocationConfig.gnssSpoofEnabled) }
    var gnssInFix by remember { mutableStateOf(TestLocationConfig.gnssInFix) }
    var gnssSpoofCoords by remember { mutableStateOf(TestLocationConfig.gnssSpoofCoords) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Test your app's fallback logic by injecting mock location data.\nEnable individual checkboxes to override real sensors.",
            color = foregroundColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()
        )

        val context = LocalContext.current
        val openDocumentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        var line = reader.readLine()
                        while (line != null) {
                            val parts = line.split("\t")
                            if (parts.size >= 3) {
                                val method = parts[0]
                                val lat = parts[1]
                                val lon = parts[2]
                                if (method == "Network") {
                                    networkSpoofCoords = "$lat, $lon"
                                    networkAvailable = true
                                } else if (method == "GNSS") {
                                    gnssSpoofCoords = "$lat, $lon"
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("", modifier = Modifier.width(40.dp)) // Spacer for checkbox column
                    Text("Method", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor)
                    Text("Fix/Avail", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor)
                    Text("Lat, Lon", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = foregroundColor)
                }

                Divider(color = Color.Gray)

                // Network Row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = networkSpoofEnabled,
                        onCheckedChange = { networkSpoofEnabled = it },
                        modifier = Modifier.width(40.dp)
                    )
                    Text("Network", modifier = Modifier.weight(1f), color = foregroundColor)
                    Checkbox(
                        checked = networkAvailable,
                        onCheckedChange = { networkAvailable = it },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = networkSpoofCoords,
                        onValueChange = { networkSpoofCoords = it },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = foregroundColor)
                    )
                }

                // GNSS Row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = gnssSpoofEnabled,
                        onCheckedChange = { gnssSpoofEnabled = it },
                        modifier = Modifier.width(40.dp)
                    )
                    Text("GNSS", modifier = Modifier.weight(1f), color = foregroundColor)
                    OutlinedTextField(
                        value = gnssInFix,
                        onValueChange = { gnssInFix = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = foregroundColor)
                    )
                    OutlinedTextField(
                        value = gnssSpoofCoords,
                        onValueChange = { gnssSpoofCoords = it },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = foregroundColor)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        openDocumentLauncher.launch(arrayOf("text/tab-separated-values", "text/csv", "*/*"))
                    }) {
                        Text("📂 Load TSV")
                    }
                    Button(onClick = {
                        TestLocationConfig.networkSpoofEnabled = networkSpoofEnabled
                        TestLocationConfig.networkAvailable = networkAvailable
                        TestLocationConfig.networkSpoofCoords = networkSpoofCoords

                        TestLocationConfig.gnssSpoofEnabled = gnssSpoofEnabled
                        TestLocationConfig.gnssInFix = gnssInFix
                        TestLocationConfig.gnssSpoofCoords = gnssSpoofCoords

                        // Testing mode is implicitly active if either toggle is true
                        TestLocationConfig.isTestingMode = gnssSpoofEnabled || networkSpoofEnabled
                    }) {
                        Text("Apply Overrides")
                    }
                }
            }
        }
    }
}

package com.example.nexuswallet

import android.util.Log
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun PinEntryDialog(
    showDialog: Boolean,
    title: String = "Enter PIN",
    subtitle: String = "Enter your PIN to continue",
    maxLength: Int = 6,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Log.d("PIN_UI_DEBUG", " PinEntryDialog - showDialog: $showDialog")

    if (showDialog) {
        Log.d("PIN_UI_DEBUG", " Showing PIN dialog")

        Dialog(onDismissRequest = {
            Log.d("PIN_UI_DEBUG", " Dialog dismissed by user")
            onDismiss()
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Log.d("PIN_UI_DEBUG", " Dialog content rendered")

                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN Entry
                    PinEntryField(
                        maxLength = maxLength,
                        onPinEntered = { enteredPin ->
                            Log.d("PIN_UI_DEBUG", " PinEntryField callback: '$enteredPin'")
                            onPinEntered(enteredPin)
                        },
                        onComplete = { completedPin ->
                            Log.d("PIN_UI_DEBUG", " PIN complete in field: '$completedPin'")
                            onPinEntered(completedPin)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // TEST BUTTON - For debugging
                    Button(
                        onClick = {
                            Log.d("PIN_UI_DEBUG", " Test PIN button clicked")
                            onPinEntered("123456")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Test PIN (123456)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cancel Button
                    TextButton(
                        onClick = {
                            Log.d("PIN_UI_DEBUG", " Cancel button clicked")
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinEntryField(
    maxLength: Int = 6,
    onPinEntered: (String) -> Unit,
    onComplete: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        // Visual PIN dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            for (i in 0 until maxLength) {
                PinDot(
                    filled = i < pin.length,
                    error = error
                )
            }
        }

        // Text field for PIN entry
        OutlinedTextField(
            value = pin,
            onValueChange = { newValue ->
                Log.d("PIN_FIELD_DEBUG", " onValueChange: '$newValue' (prev: '$pin')")

                if (newValue.length <= maxLength && newValue.all { it.isDigit() }) {
                    pin = newValue
                    error = false

                    // Only call onComplete when PIN is full length
                    if (newValue.length == maxLength) {
                        Log.d("PIN_FIELD_DEBUG", " PIN complete! Calling onComplete")
                        onComplete(newValue)
                    } else {
                        Log.d("PIN_FIELD_DEBUG", "PIN not complete yet (${newValue.length}/$maxLength)")
                        // Don't call onPinEntered for partial PINs!
                    }
                } else {
                    Log.d("PIN_FIELD_DEBUG", " Invalid input")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("Enter $maxLength-digit PIN") },
            singleLine = true,
            isError = error
        )

        // Debug info
        Text(
            text = "Debug: PIN = '$pin' (${pin.length}/$maxLength)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Error message
        if (error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Incorrect PIN",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Manual submit button
        Button(
            onClick = {
                Log.d("PIN_FIELD_DEBUG", " Manual submit clicked")
                if (pin.length == maxLength) {
                    Log.d("PIN_FIELD_DEBUG", "Submitting PIN: '$pin'")
                    onComplete(pin)
                } else {
                    Log.d("PIN_FIELD_DEBUG", " PIN not complete (${pin.length}/$maxLength)")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length == maxLength
        ) {
            Text("Submit PIN")
        }
    }
}

@Composable
fun PinDot(
    filled: Boolean,
    error: Boolean = false
) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                color = color,
                shape = MaterialTheme.shapes.small
            )
    )
}
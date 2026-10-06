package app.haven.companion.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.haven.companion.data.ApiException
import app.haven.companion.data.AuthRepository
import app.haven.companion.data.Config
import app.haven.companion.data.NetworkException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(auth: AuthRepository) {
    var createAccount by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun friendly(e: Exception) = when (e) {
        is NetworkException -> Config.NETWORK_ERROR_MESSAGE
        is ApiException -> e.message ?: "Something went wrong."
        else -> "Something went wrong."
    }

    fun submit() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                if (createAccount) {
                    if (auth.signUp(email, password) == AuthRepository.SignUpResult.ConfirmEmail) {
                        message = "Check your email to confirm your address, then sign in."
                        createAccount = false
                    }
                } else {
                    auth.signIn(email, password)
                }
            } catch (e: Exception) {
                message = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(if (createAccount) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your account keeps your settings and memories private to you and lets you export or delete them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim() }, singleLine = true, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            supportingText = { if (createAccount) Text("At least 8 characters") },
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
        Button(
            onClick = ::submit,
            enabled = !busy && email.contains('@') && password.length >= (if (createAccount) 8 else 1),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (createAccount) "Create account" else "Sign in")
        }
        TextButton(onClick = { createAccount = !createAccount; message = null }) {
            Text(if (createAccount) "I already have an account" else "Create a new account")
        }
        if (!createAccount) {
            TextButton(
                enabled = email.contains('@') && !busy,
                onClick = {
                    scope.launch {
                        message = try {
                            auth.sendPasswordReset(email)
                            "If that address has an account, a reset link is on its way."
                        } catch (e: Exception) {
                            friendly(e)
                        }
                    }
                },
            ) { Text("Forgot password?") }
        }
    }
}

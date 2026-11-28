package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.R
import com.example.ui.theme.BrightCyanBlue
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMidnight
import com.example.ui.theme.DarkNavBar
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.data.NetworkMonitor
import com.example.viewmodel.ChangeEmailStage
import com.example.viewmodel.ChangeEmailUiState
import kotlinx.coroutines.delay

@Composable
fun VerificationCodeFormContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    targetEmail: String,
    buttonText: String,
    codeTestTag: String,
    submitTestTag: String,
    resendTestTag: String,
    backLabel: String,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    onClearMessages: () -> Unit,
    isOnline: Boolean = true,
    initialResendCooldown: Int = 30,
    extraFieldsContent: (@Composable ColumnScope.(onClear: () -> Unit) -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    var code by remember(targetEmail, title) { mutableStateOf("") }
    var resendCooldown by remember(targetEmail, title) {
        mutableIntStateOf(if (!isOnline || errorMessage != null) 0 else initialResendCooldown)
    }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000L)
            resendCooldown -= 1
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(ElectricBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrightCyanBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (targetEmail.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = targetEmail,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                val filtered = input.filter { it.isLetterOrDigit() }.take(10)
                code = filtered
                onClearMessages()
            },
            label = { Text("Verification code") },
            placeholder = { Text("Enter 6-digit code") },
            leadingIcon = { Icon(Icons.Filled.Pin, contentDescription = null) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (extraFieldsContent == null) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (extraFieldsContent == null) onVerify(code)
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(codeTestTag)
        )

        extraFieldsContent?.invoke(this, onClearMessages)

        val cardErrorMessage = errorMessage?.takeUnless { it == NetworkMonitor.OFFLINE_ERROR_MESSAGE }
        AuthStatusBanners(errorMessage = cardErrorMessage, infoMessage = infoMessage)

        Button(
            onClick = { onVerify(code) },
            enabled = !isLoading,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .testTag(submitTestTag)
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                enabled = !isLoading
            ) {
                Text(backLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TextButton(
                onClick = {
                    resendCooldown = 30
                    onResend()
                },
                enabled = !isLoading && resendCooldown == 0,
                modifier = Modifier.testTag(resendTestTag)
            ) {
                Text(
                    text = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Code",
                    fontWeight = FontWeight.Bold,
                    color = if (resendCooldown > 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        BrightCyanBlue
                    }
                )
            }
        }
    }
}

@Composable
fun ForgotPasswordRequestFormContent(
    initialEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onSendResetCode: (String) -> Unit,
    onBackToSignIn: () -> Unit,
    onClearMessages: () -> Unit,
    isOnline: Boolean = true
) {
    BackHandler(onBack = onBackToSignIn)

    var resetEmail by remember(initialEmail) { mutableStateOf(initialEmail) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(ElectricBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LockReset,
                contentDescription = null,
                tint = BrightCyanBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Enter your ClipCash account email and we will send a verification code to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = resetEmail,
            onValueChange = {
                resetEmail = it
                onClearMessages()
            },
            label = { Text("Account email address") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSendResetCode(resetEmail) }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_password_email_input")
        )

        val cardErrorMessage = errorMessage?.takeUnless { it == NetworkMonitor.OFFLINE_ERROR_MESSAGE }
        AuthStatusBanners(errorMessage = cardErrorMessage, infoMessage = infoMessage)

        Button(
            onClick = { onSendResetCode(resetEmail) },
            enabled = !isLoading,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .testTag("reset_password_send_button")
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = "Send Reset Code",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        TextButton(
            onClick = onBackToSignIn,
            enabled = !isLoading
        ) {
            Text("Back to Sign In", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SetNewPasswordPageContent(
    targetEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onUpdatePassword: (newPassword: String, confirmPassword: String) -> Unit,
    onBack: () -> Unit,
    onClearMessages: () -> Unit
) {
    BackHandler(onBack = onBack)

    var newPassword by remember(targetEmail) { mutableStateOf("") }
    var confirmPassword by remember(targetEmail) { mutableStateOf("") }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    val mismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(ElectricBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LockReset,
                contentDescription = null,
                tint = BrightCyanBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "Create New Password",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your verification code is confirmed. Enter and confirm your new ClipCash password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (targetEmail.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = targetEmail,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        OutlinedTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                onClearMessages()
            },
            label = { Text("New password (6+ characters)") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { newVisible = !newVisible }) {
                    Icon(
                        imageVector = if (newVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (newVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_new_password_input")
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                onClearMessages()
            },
            label = { Text("Confirm new password") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(
                        imageVector = if (confirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (confirmVisible) "Hide confirm password" else "Show confirm password"
                    )
                }
            },
            isError = mismatch,
            supportingText = if (mismatch) {
                { Text("Passwords do not match") }
            } else {
                null
            },
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onUpdatePassword(newPassword, confirmPassword) }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_confirm_password_input")
        )

        val cardErrorMessage = errorMessage?.takeUnless { it == NetworkMonitor.OFFLINE_ERROR_MESSAGE }
        AuthStatusBanners(errorMessage = cardErrorMessage, infoMessage = infoMessage)

        Button(
            onClick = { onUpdatePassword(newPassword, confirmPassword) },
            enabled = !isLoading,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .testTag("reset_new_password_submit_button")
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = "Update Password",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        TextButton(
            onClick = onBack,
            enabled = !isLoading
        ) {
            Text("Back to Reset Email", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ChangeEmailScreen(
    currentEmail: String,
    state: ChangeEmailUiState,
    onVerifyCurrentEmailCode: (code: String) -> Unit,
    onResendCurrentEmailCode: () -> Unit,
    onSendNewEmail: (newEmail: String) -> Unit,
    onVerifyNewEmailCode: (code: String) -> Unit,
    onResendNewEmailCode: () -> Unit,
    onBackToEnterEmail: () -> Unit,
    onClearMessages: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        if (state.stage == ChangeEmailStage.VERIFY_NEW_EMAIL) {
            onBackToEnterEmail()
        } else {
            onNavigateBack()
        }
    }

    var newEmailInput by remember(currentEmail) { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepRoyalBlue, ElectricBlue, DeepEmerald))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        IconButton(
            onClick = {
                if (state.stage == ChangeEmailStage.VERIFY_NEW_EMAIL) {
                    onBackToEnterEmail()
                } else {
                    onNavigateBack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .testTag("change_email_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Wallet",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.clipcash_logo),
                contentDescription = "ClipCash Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(12.dp))

            NetworkStatusPill(isOnline = state.isOnline)

            if (!state.isOnline || state.errorMessage == NetworkMonitor.OFFLINE_ERROR_MESSAGE) {
                Spacer(Modifier.height(12.dp))
                OfflineInlineNotice()
            }

            Spacer(Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(22.dp)) {
                    when (state.stage) {
                        ChangeEmailStage.VERIFY_CURRENT_EMAIL -> {
                            VerificationCodeFormContent(
                                icon = Icons.Filled.MarkEmailRead,
                                title = "Verify Your Identity",
                                subtitle = "Enter the verification code sent to your current email before entering a new email address.",
                                targetEmail = state.emailBeingVerified.ifBlank { currentEmail },
                                buttonText = "Verify & Continue",
                                codeTestTag = "verify_current_email_code_input",
                                submitTestTag = "verify_current_email_submit_button",
                                resendTestTag = "verify_current_email_resend_button",
                                backLabel = "Cancel",
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                infoMessage = state.infoMessage,
                                onVerify = onVerifyCurrentEmailCode,
                                onResend = onResendCurrentEmailCode,
                                onBack = onNavigateBack,
                                onClearMessages = onClearMessages,
                                isOnline = state.isOnline,
                                initialResendCooldown = if (state.infoMessage != null) 30 else 0
                            )
                        }

                        ChangeEmailStage.ENTER_NEW_EMAIL -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(ElectricBlue.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AlternateEmail,
                                        contentDescription = null,
                                        tint = BrightCyanBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = "New Email Address",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "Enter the new email address you want linked to your ClipCash account.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                if (currentEmail.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Current email",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = currentEmail,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = newEmailInput,
                                    onValueChange = {
                                        newEmailInput = it
                                        onClearMessages()
                                    },
                                    label = { Text("New email address") },
                                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { onSendNewEmail(newEmailInput) }
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("change_email_new_input")
                                )

                                AuthStatusBanners(
                                    errorMessage = state.errorMessage?.takeUnless {
                                        it == NetworkMonitor.OFFLINE_ERROR_MESSAGE
                                    },
                                    infoMessage = state.infoMessage
                                )

                                Button(
                                    onClick = { onSendNewEmail(newEmailInput) },
                                    enabled = !state.isLoading,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricBlue,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .testTag("change_email_send_button")
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Update Email Address",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = onNavigateBack,
                                    enabled = !state.isLoading
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }

                        ChangeEmailStage.VERIFY_NEW_EMAIL -> {
                            VerificationCodeFormContent(
                                icon = Icons.Filled.MarkEmailRead,
                                title = "Verify New Email",
                                subtitle = "Enter the verification code sent to confirm your new email address.",
                                targetEmail = state.emailBeingVerified,
                                buttonText = "Verify & Save Email",
                                codeTestTag = "verify_change_email_code_input",
                                submitTestTag = "verify_change_email_submit_button",
                                resendTestTag = "verify_change_email_resend_button",
                                backLabel = "Change Email",
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                infoMessage = state.infoMessage,
                                onVerify = onVerifyNewEmailCode,
                                onResend = onResendNewEmailCode,
                                onBack = onBackToEnterEmail,
                                onClearMessages = onClearMessages,
                                isOnline = state.isOnline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthStatusBanners(
    errorMessage: String?,
    infoMessage: String?
) {
    errorMessage?.let { err ->
        NetworkErrorCard(message = err)
    }

    infoMessage?.let { info ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CashEmerald.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = info,
                style = MaterialTheme.typography.bodyMedium,
                color = CashEmerald,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun NetworkStatusPill(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isOnline) CashEmerald else CoralRed
    val background = if (isOnline) {
        Color(0xFF0A2E25).copy(alpha = 0.84f)
    } else {
        Color(0xFF3B1219).copy(alpha = 0.90f)
    }
    Surface(
        shape = CircleShape,
        color = background,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier.testTag("network_connection_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = if (isOnline) "Network connected" else "No internet connection",
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isOnline) "Network Connected" else "No Internet Connection",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

@Composable
fun NetworkErrorCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF7D241C),
        border = BorderStroke(1.dp, Color(0xFFB91C1C).copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = "Network error",
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFFEE2E2)
            )
        }
    }
}

@Composable
fun OfflineInlineNotice(
    modifier: Modifier = Modifier
) {
    NetworkErrorCard(
        message = NetworkMonitor.OFFLINE_ERROR_MESSAGE,
        modifier = modifier.testTag("offline_inline_notice")
    )
}

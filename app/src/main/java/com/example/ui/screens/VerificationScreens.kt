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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
    extraFieldsContent: (@Composable ColumnScope.(onClear: () -> Unit) -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    var code by remember(targetEmail, title) { mutableStateOf("") }
    var resendCooldown by remember(targetEmail, title) { mutableIntStateOf(30) }

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

        AuthStatusBanners(errorMessage = errorMessage, infoMessage = infoMessage)

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
    onClearMessages: () -> Unit
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

        AuthStatusBanners(errorMessage = errorMessage, infoMessage = infoMessage)

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
fun ResetPasswordVerifyPageContent(
    targetEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onVerifyAndReset: (code: String, newPassword: String, confirmPassword: String) -> Unit,
    onResendCode: () -> Unit,
    onBack: () -> Unit,
    onClearMessages: () -> Unit
) {
    var newPassword by remember(targetEmail) { mutableStateOf("") }
    var confirmPassword by remember(targetEmail) { mutableStateOf("") }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    val mismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword

    VerificationCodeFormContent(
        icon = Icons.Filled.LockReset,
        title = "Verify Reset Code",
        subtitle = "Enter the verification code sent to your email along with your new password.",
        targetEmail = targetEmail,
        buttonText = "Verify & Reset Password",
        codeTestTag = "verify_reset_code_input",
        submitTestTag = "verify_reset_submit_button",
        resendTestTag = "verify_reset_resend_button",
        backLabel = "Change Email",
        isLoading = isLoading,
        errorMessage = errorMessage,
        infoMessage = infoMessage,
        onVerify = { code -> onVerifyAndReset(code, newPassword, confirmPassword) },
        onResend = onResendCode,
        onBack = onBack,
        onClearMessages = onClearMessages
    ) { onClear ->
        OutlinedTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                onClear()
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
                onClear()
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_confirm_password_input")
        )
    }
}

@Composable
fun ChangeEmailScreen(
    currentEmail: String,
    state: ChangeEmailUiState,
    onSendCode: (newEmail: String) -> Unit,
    onVerifyCode: (code: String) -> Unit,
    onResendCode: () -> Unit,
    onBackToEnterEmail: () -> Unit,
    onClearMessages: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        if (state.stage == ChangeEmailStage.VERIFY_CODE) {
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
                if (state.stage == ChangeEmailStage.VERIFY_CODE) {
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

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(22.dp)) {
                    when (state.stage) {
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
                                    text = "Change Email Address",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "We will send a verification code to your new email address before updating your ClipCash account.",
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
                                        onDone = { onSendCode(newEmailInput) }
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("change_email_new_input")
                                )

                                AuthStatusBanners(
                                    errorMessage = state.errorMessage,
                                    infoMessage = state.infoMessage
                                )

                                Button(
                                    onClick = { onSendCode(newEmailInput) },
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
                                            text = "Send Verification Code",
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

                        ChangeEmailStage.VERIFY_CODE -> {
                            VerificationCodeFormContent(
                                icon = Icons.Filled.MarkEmailRead,
                                title = "Verify New Email",
                                subtitle = "Enter the verification code sent to confirm your new email address.",
                                targetEmail = state.emailBeingVerified,
                                buttonText = "Verify & Update Email",
                                codeTestTag = "verify_change_email_code_input",
                                submitTestTag = "verify_change_email_submit_button",
                                resendTestTag = "verify_change_email_resend_button",
                                backLabel = "Change Email",
                                isLoading = state.isLoading,
                                errorMessage = state.errorMessage,
                                infoMessage = state.infoMessage,
                                onVerify = onVerifyCode,
                                onResend = onResendCode,
                                onBack = onBackToEnterEmail,
                                onClearMessages = onClearMessages
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
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CoralRed.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = CoralRed,
                modifier = Modifier.padding(12.dp)
            )
        }
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

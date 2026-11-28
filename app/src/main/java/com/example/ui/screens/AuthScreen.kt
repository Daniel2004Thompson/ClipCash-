package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.NetworkMonitor
import com.example.model.ClipCatalog
import com.example.model.NationalityOption
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.BrightCyanBlue
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMidnight
import com.example.ui.theme.DarkNavBar
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.viewmodel.AuthStage
import com.example.viewmodel.AuthUiState

@Composable
fun AuthScreen(
    authState: AuthUiState,
    onStageChange: (AuthStage) -> Unit,
    onSignIn: (String, String) -> Unit,
    onVerifySignInCode: (String) -> Unit,
    onResendSignInCode: () -> Unit,
    onSignUp: (String, String, NationalityOption, String, String, String, String) -> Unit,
    onVerifySignUpCode: (String) -> Unit,
    onResendSignUpCode: () -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onVerifyResetCode: (String) -> Unit,
    onCompletePasswordReset: (String, String) -> Unit,
    onResendPasswordResetCode: () -> Unit,
    onClearMessages: () -> Unit
) {
    val isSignUpMode = authState.stage == AuthStage.SIGN_UP
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedNationality by remember { mutableStateOf(ClipCatalog.nationalities.first()) }
    var showNationalityPicker by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val passwordsMismatch = isSignUpMode && confirmPassword.isNotEmpty() && confirmPassword != password

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepRoyalBlue, ElectricBlue, DeepEmerald))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
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
                    .size(110.dp)
                    .clip(RoundedCornerShape(26.dp))
            )

            Spacer(Modifier.height(14.dp))

            val headerTitle = when (authState.stage) {
                AuthStage.SIGN_IN -> "Welcome to ClipCash"
                AuthStage.SIGN_UP -> "Create ClipCash Account"
                AuthStage.VERIFY_SIGN_IN -> "Verify Your Sign In"
                AuthStage.VERIFY_SIGN_UP -> "Verify Your Account"
                AuthStage.FORGOT_PASSWORD -> "Reset Your Password"
                AuthStage.VERIFY_RESET_PASSWORD -> "Verify Reset Code"
                AuthStage.RESET_NEW_PASSWORD -> "Create New Password"
            }

            Text(
                text = headerTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Watch Videos. Play Mini Games. Earn Rewards.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            NetworkStatusPill(isOnline = authState.isOnline)

            if (!authState.isOnline || authState.errorMessage == NetworkMonitor.OFFLINE_ERROR_MESSAGE) {
                Spacer(Modifier.height(14.dp))
                OfflineInlineNotice()
            }

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(22.dp)) {
                    when (authState.stage) {
                        AuthStage.VERIFY_SIGN_IN -> VerificationCodeFormContent(
                            icon = Icons.Filled.MarkEmailRead,
                            title = "Sign-In Verification Code",
                            subtitle = "Enter the verification code sent to your email to unlock your ClipCash account.",
                            targetEmail = authState.pendingEmail,
                            buttonText = "Verify & Sign In",
                            codeTestTag = "verify_signin_code_input",
                            submitTestTag = "verify_signin_submit_button",
                            resendTestTag = "verify_signin_resend_button",
                            backLabel = "Back to Sign In",
                            isLoading = authState.isLoading,
                            errorMessage = authState.errorMessage,
                            infoMessage = authState.infoMessage,
                            onVerify = onVerifySignInCode,
                            onResend = onResendSignInCode,
                            onBack = { onStageChange(AuthStage.SIGN_IN) },
                            onClearMessages = onClearMessages,
                            isOnline = authState.isOnline
                        )

                        AuthStage.VERIFY_SIGN_UP -> VerificationCodeFormContent(
                            icon = Icons.Filled.VerifiedUser,
                            title = "Verify Your Email",
                            subtitle = "Enter the verification code sent to your email to finish creating your ClipCash account.",
                            targetEmail = authState.pendingEmail,
                            buttonText = "Verify & Activate Account",
                            codeTestTag = "verify_signup_code_input",
                            submitTestTag = "verify_signup_submit_button",
                            resendTestTag = "verify_signup_resend_button",
                            backLabel = "Back to Sign Up",
                            isLoading = authState.isLoading,
                            errorMessage = authState.errorMessage,
                            infoMessage = authState.infoMessage,
                            onVerify = onVerifySignUpCode,
                            onResend = onResendSignUpCode,
                            onBack = { onStageChange(AuthStage.SIGN_UP) },
                            onClearMessages = onClearMessages,
                            isOnline = authState.isOnline
                        )

                        AuthStage.FORGOT_PASSWORD -> ForgotPasswordRequestFormContent(
                            initialEmail = email,
                            isLoading = authState.isLoading,
                            errorMessage = authState.errorMessage,
                            infoMessage = authState.infoMessage,
                            onSendResetCode = { entered ->
                                email = entered
                                onRequestPasswordReset(entered)
                            },
                            onBackToSignIn = { onStageChange(AuthStage.SIGN_IN) },
                            onClearMessages = onClearMessages,
                            isOnline = authState.isOnline
                        )

                        AuthStage.VERIFY_RESET_PASSWORD -> VerificationCodeFormContent(
                            icon = Icons.Filled.LockReset,
                            title = "Verify Reset Code",
                            subtitle = "Enter the verification code sent to your email before creating a new password.",
                            targetEmail = authState.pendingEmail,
                            buttonText = "Verify Code",
                            codeTestTag = "verify_reset_code_input",
                            submitTestTag = "verify_reset_submit_button",
                            resendTestTag = "verify_reset_resend_button",
                            backLabel = "Change Email",
                            isLoading = authState.isLoading,
                            errorMessage = authState.errorMessage,
                            infoMessage = authState.infoMessage,
                            onVerify = onVerifyResetCode,
                            onResend = onResendPasswordResetCode,
                            onBack = { onStageChange(AuthStage.FORGOT_PASSWORD) },
                            onClearMessages = onClearMessages,
                            isOnline = authState.isOnline
                        )

                        AuthStage.RESET_NEW_PASSWORD -> SetNewPasswordPageContent(
                            targetEmail = authState.pendingEmail,
                            isLoading = authState.isLoading,
                            errorMessage = authState.errorMessage,
                            infoMessage = authState.infoMessage,
                            onUpdatePassword = onCompletePasswordReset,
                            onBack = { onStageChange(AuthStage.FORGOT_PASSWORD) },
                            onClearMessages = onClearMessages
                        )

                        AuthStage.SIGN_IN,
                        AuthStage.SIGN_UP -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                onClearMessages()
                            },
                            label = { Text("Full name") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_full_name_input")
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onClearMessages()
                            },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username_input")
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_nationality_input")
                                .clickable { showNationalityPicker = true }
                        ) {
                            OutlinedTextField(
                                value = selectedNationality.displayLabel,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Nationality") },
                                leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select nationality") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { input ->
                                phoneNumber = input.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                                onClearMessages()
                            },
                            label = { Text("Phone number") },
                            placeholder = { Text("801 234 5678") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                            prefix = {
                                Text(
                                    text = "${selectedNationality.flagEmoji} ${selectedNationality.dialCode} ",
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrightCyanBlue
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_phone_input")
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            onClearMessages()
                        },
                        label = { Text("Email address") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onClearMessages()
                        },
                        label = { Text("Password (6+ characters)") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isSignUpMode) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isSignUpMode) onSignIn(email, password)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                onClearMessages()
                            },
                            label = { Text("Confirm password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (confirmPasswordVisible) "Hide confirm password" else "Show confirm password"
                                    )
                                }
                            },
                            isError = passwordsMismatch,
                            supportingText = if (passwordsMismatch) {
                                { Text("Passwords do not match") }
                            } else {
                                null
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onSignUp(
                                        fullName,
                                        username,
                                        selectedNationality,
                                        phoneNumber,
                                        email,
                                        password,
                                        confirmPassword
                                    )
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_confirm_password_input")
                        )
                    }

                    if (!isSignUpMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onStageChange(AuthStage.FORGOT_PASSWORD) },
                                modifier = Modifier.testTag("auth_forgot_password_button")
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrightCyanBlue
                                )
                            }
                        }
                    }

                    AuthStatusBanners(
                        errorMessage = authState.errorMessage?.takeUnless {
                            it == NetworkMonitor.OFFLINE_ERROR_MESSAGE
                        },
                        infoMessage = authState.infoMessage
                    )

                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                onSignUp(
                                    fullName,
                                    username,
                                    selectedNationality,
                                    phoneNumber,
                                    email,
                                    password,
                                    confirmPassword
                                )
                            } else {
                                onSignIn(email, password)
                            }
                        },
                        enabled = !authState.isLoading,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .testTag("auth_submit_button")
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = if (isSignUpMode) "Create Account" else "Sign In",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignUpMode) "Already have an account?" else "New to ClipCash?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                onStageChange(if (isSignUpMode) AuthStage.SIGN_IN else AuthStage.SIGN_UP)
                            },
                            modifier = Modifier.testTag("auth_toggle_mode_button")
                        ) {
                            Text(
                                text = if (isSignUpMode) "Sign In" else "Sign Up",
                                fontWeight = FontWeight.Bold,
                                color = BrightCyanBlue
                            )
                        }
                    }
                        }
                    }
                }
            }
        }
    }

    if (showNationalityPicker) {
        NationalityPickerDialog(
            selected = selectedNationality,
            onSelect = { option ->
                selectedNationality = option
                showNationalityPicker = false
                onClearMessages()
            },
            onDismiss = { showNationalityPicker = false }
        )
    }
}

@Composable
private fun NationalityPickerDialog(
    selected: NationalityOption,
    onSelect: (NationalityOption) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        val q = search.trim()
        if (q.isEmpty()) {
            ClipCatalog.nationalities
        } else {
            ClipCatalog.nationalities.filter {
                it.countryName.contains(q, ignoreCase = true) ||
                    it.nationality.contains(q, ignoreCase = true) ||
                    it.dialCode.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Nationality") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search country or nationality...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.isoCode }) { option ->
                        val isSelected = option.isoCode == selected.isoCode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${option.flagEmoji} ${option.nationality}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${option.countryName} · ${option.dialCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = BrightCyanBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

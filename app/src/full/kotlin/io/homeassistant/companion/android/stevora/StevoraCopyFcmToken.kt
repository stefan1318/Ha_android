package io.homeassistant.companion.android.stevora

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal fun StevoraCopyFcmToken(bg: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<Boolean?>(null) }

    OutlinedButton(enabled = !loading, onClick = {
        loading = true
        copied = null
        scope.launch {
            try {
                val token = suspendCancellableCoroutine<String> { continuation ->
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (continuation.isActive) {
                            if (task.isSuccessful) continuation.resume(task.result)
                            else continuation.resumeWithException(task.exception ?: IllegalStateException())
                        }
                    }
                }
                check(token.isNotBlank())
                val clip = ClipData.newPlainText("FCM", token)
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
                checkNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
                copied = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                copied = false
            } finally {
                loading = false
            }
        }
    }) {
        Text(if (bg) "Копирай FCM токена" else "Copy FCM token")
    }
    when (copied) {
        true -> Text(if (bg) "Токенът е копиран." else "Token copied.")
        false -> Text(if (bg) "Неуспешно копиране. Опитай отново." else "Could not copy the token. Try again.")
        null -> Unit
    }
}

package br.com.yurimedeiros.meufinanceiro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Stable
internal class ModalTitleEditState {
    var editing by mutableStateOf(false)
    var draft by mutableStateOf(TextFieldValue(""))
}

/** State belongs to the form, so closing a locked dialog preserves its draft. */
@Composable
internal fun EditableModalTitle(
    value: String, change: (String) -> Unit, modifier: Modifier = Modifier, editState: ModalTitleEditState,
    original: String? = null, placeholder: String = "Título", enabled: Boolean = true,
) {
    val editing = editState.editing
    val draft = editState.draft
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = MaterialTheme.colorScheme.primary
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    fun begin() { if (enabled) { editState.draft = TextFieldValue(value, TextRange(0, value.length)); editState.editing = true } }
    fun finish(save: Boolean) {
        val current = editState.draft.text
        if (save && (current.isBlank() || current.trim().length > 120)) return
        if (save) change(current.trim())
        editState.editing = false; focus.clearFocus(); keyboard?.hide()
    }
    BackHandler(editing) { finish(false) }
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (editing) {
            BasicTextField(draft, { editState.draft = it }, enabled = enabled, singleLine = true, textStyle = style,
                cursorBrush = SolidColor(accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finish(true) }),
                modifier = Modifier.fillMaxWidth().focusRequester(requester).padding(bottom = 7.dp)
                    .drawBehind { drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value.ifBlank { placeholder }, style = style, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable(enabled = enabled, role = Role.Button, onClick = ::begin))
            IconButton(onClick = ::begin, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(EditTitleIcon, "Editar título", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        original?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (editing) {
        // Android's visible popup frame excludes the IME, so this sits directly above it.
        val position = remember { object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                IntOffset(((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0), (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        } }
        Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
                modifier = Modifier.width((LocalConfiguration.current.screenWidthDp.dp - 24.dp).coerceAtMost(420.dp)).padding(bottom = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { finish(false) }, enabled = enabled) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { finish(true) }, enabled = enabled && draft.text.isNotBlank() && draft.text.trim().length <= 120) { Text("Concluir") }
                }
            }
        }
    }
}

package com.pulso.tv.ui.pulso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulso.tv.model.chat.ChatMessage
import com.pulso.tv.ui.chat.ChatViewModel
import com.pulso.tv.util.prefs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val MAX_MESSAGES = 200

/**
 * Pulso's own chat column. Pulso's ChatViewModel is only the engine here
 * (IRC connection, recent messages, parsing); nothing of its screens is used.
 */
@Composable
fun PulsoChat(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        // subscribe first (undispatched) so nothing emitted while the snapshot is taken is lost
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.newMessage.collect { (message, _, removeCount) ->
                messages.add(message)
                while (messages.size > MAX_MESSAGES) messages.removeAt(0)
                // with a subscriber the engine leaves trimming of its own list to the consumer
                if (removeCount > 0) synchronized(viewModel.chatMessages) {
                    repeat(minOf(removeCount, viewModel.chatMessages.size)) { viewModel.chatMessages.removeAt(0) }
                }
            }
        }
        // recent messages arrive after connecting and go before what is already shown
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.addMessages.collect { (older, _) ->
                messages.addAll(0, older.takeLast((MAX_MESSAGES - messages.size).coerceAtLeast(0)))
            }
        }
        // cleared chat (disconnect): drop that many from the start
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.removeMessages.collect { count ->
                repeat(minOf(count, messages.size)) { messages.removeAt(0) }
            }
        }
        synchronized(viewModel.chatMessages) {
            messages.addAll(0, viewModel.chatMessages.takeLast(100))
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Box(modifier.background(Pulso.Surface)) {
        if (messages.isEmpty()) {
            Text(
                tr("Conectando ao chat…"),
                color = Pulso.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(messages) { message -> ChatLine(message, viewModel) }
        }
    }
}

private val EMOTE_SIZE = 24.sp
private val BADGE_SIZE = 16.sp

private fun inlineImage(url: String, size: TextUnit) = InlineTextContent(
    Placeholder(size, size, PlaceholderVerticalAlign.Center)
) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ChatLine(message: ChatMessage, viewModel: ChatViewModel) {
    val name = message.userName
    val inline = HashMap<String, InlineTextContent>()
    val thirdParty = viewModel.thirdPartyEmotes.toList().filter { it.name != null }.associateBy { it.name!! }

    fun badgeUrl(setId: String, version: String): String? {
        val found = viewModel.channelBadges.toList().find { it.setId == setId && it.version == version }
            ?: viewModel.globalBadges.toList().find { it.setId == setId && it.version == version }
        return found?.let { it.url2x ?: it.url1x ?: it.url3x ?: it.url4x }
    }

    val text = buildAnnotatedString {
        var n = 0
        fun emote(url: String, size: TextUnit, alt: String) {
            val id = "i${n++}"
            inline[id] = inlineImage(url, size)
            appendInlineContent(id, alt)
        }

        if (name.isNullOrBlank()) {
            withStyle(SpanStyle(color = Pulso.TextSecondary)) {
                append(message.systemMsg ?: message.message.orEmpty())
            }
            return@buildAnnotatedString
        }

        message.badges.orEmpty().forEach { badge ->
            badgeUrl(badge.setId, badge.version)?.let {
                emote(it, BADGE_SIZE, "[b]")
                append(" ")
            }
        }
        withStyle(SpanStyle(color = userColor(message.color), fontWeight = FontWeight.Bold)) { append(name) }
        withStyle(SpanStyle(color = Pulso.TextPrimary)) { append(": ") }

        val body = message.message.orEmpty()
        fun words(s: String) {
            if (s.isEmpty()) return
            s.split(' ').forEachIndexed { i, word ->
                if (i > 0) append(" ")
                val tp = thirdParty[word]
                val url = tp?.let { it.url2x ?: it.url1x ?: it.url3x ?: it.url4x }
                if (url != null) emote(url, EMOTE_SIZE, word)
                else withStyle(SpanStyle(color = Pulso.TextPrimary)) { append(word) }
            }
        }

        var index = 0
        message.emotes.orEmpty().sortedBy { it.begin }.forEach { e ->
            val url = e.url2x ?: e.url1x ?: e.url3x ?: e.url4x
            if (url != null && e.begin >= index && e.end < body.length && e.begin <= e.end) {
                words(body.substring(index, e.begin))
                emote(url, EMOTE_SIZE, body.substring(e.begin, e.end + 1))
                index = e.end + 1
            }
        }
        words(body.substring(index.coerceAtMost(body.length)))
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val size = remember { ctx.prefs().getInt(PulsoSettingsActivity.PREF_CHAT_TEXT, 14) }
    Text(
        text,
        inlineContent = inline,
        fontSize = size.sp,
        lineHeight = (size * 1.85f).sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

private fun userColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Pulso.Yellow)

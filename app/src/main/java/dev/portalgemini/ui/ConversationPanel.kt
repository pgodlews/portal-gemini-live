package dev.portalgemini.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.portalgemini.model.ChatMessage
import dev.portalgemini.model.ChatSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationPanel(
    messages: List<ChatMessage>,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit = {},
    isSessionEnded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isSessionEnded) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }

    Surface(
        modifier = modifier
            .width(560.dp)
            .height(420.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(20.dp))
            .shadow(24.dp, RoundedCornerShape(20.dp)),
        color = Color(0xF2101218),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF202636)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Conversation History",
                            tint = Color(0xFF80D8FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "Conversation History",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E212B)
                    ) {
                        Text(
                            text = "${messages.size}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF9AA0A6)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onExport,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Conversation",
                                tint = Color(0xFF80D8FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear History",
                                tint = Color(0xFF9AA0A6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close History",
                            tint = Color(0xFF9AA0A6),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = Color(0xFF373E4E),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No messages yet in this session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF80868B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Start speaking to Gemini to see your conversation turns appear here in real time.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color(0xFF5F6368),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    val maxSessionIndex = messages.maxOfOrNull { it.sessionIndex } ?: 1
                    var prevSessionIndex = -1

                    for (msg in messages) {
                        val isNewSession = msg.sessionIndex != prevSessionIndex
                        if (isNewSession) {
                            if (prevSessionIndex != -1) {
                                item(key = "divider_$prevSessionIndex") {
                                    SessionEndedDivider(sessionIndex = prevSessionIndex)
                                }
                            }
                            item(key = "header_${msg.sessionIndex}") {
                                SessionHeader(
                                    sessionIndex = msg.sessionIndex,
                                    isCurrent = (msg.sessionIndex == maxSessionIndex && !isSessionEnded)
                                )
                            }
                            prevSessionIndex = msg.sessionIndex
                        }
                        item(key = msg.id) {
                            MessageBubble(message = msg)
                        }
                    }

                    // If the current session has ended, show the divider at the end
                    if (isSessionEnded) {
                        item(key = "divider_ended_$maxSessionIndex") {
                            SessionEndedDivider(sessionIndex = maxSessionIndex)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(sessionIndex: Int, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isCurrent) Color(0x2238BDF8) else Color(0xFF1A1D26),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isCurrent) Color(0x6638BDF8) else Color(0xFF2C303E)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) Color(0xFF38BDF8) else Color(0xFF9AA0A6))
                )
                Text(
                    text = if (isCurrent) "Session $sessionIndex (Active)" else "Session $sessionIndex",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = if (isCurrent) Color(0xFF38BDF8) else Color(0xFF9AA0A6)
                )
            }
        }
    }
}

@Composable
private fun SessionEndedDivider(sessionIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF2C303E)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E212B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C303E)),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
                Text(
                    text = "Session $sessionIndex Ended · Context Reset",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF9AA0A6)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF2C303E)
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == ChatSender.USER
    val timeStr = remember(message.timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestampMs))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = if (isUser) Color(0xFF1E2838) else Color(0xFF171A24),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) Color(0xFF2B3A52) else Color(0xFF2C303E)
            ),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isUser) "You" else "Gemini",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (isUser) Color(0xFF80D8FF) else Color(0xFFCE93D8)
                    )

                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF757B88)
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = if (isUser) Color(0xFFE3F2FD) else Color(0xFFE8EAED)
                )
            }
        }
    }
}

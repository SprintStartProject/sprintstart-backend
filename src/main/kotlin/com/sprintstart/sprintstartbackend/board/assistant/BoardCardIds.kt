package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

internal fun JsonElement.asCardId(): UUID? =
    (this as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

internal fun onBoard(ids: List<UUID>, cards: List<BoardCardResponse>): Found<List<BoardCardResponse>> =
    when {
        cards.isEmpty() -> Found.Refused(
            "None of those ids are cards on the hire's board. Read read_board again and pass " +
                "the ids it gives.",
        )
        cards.size < ids.size -> Found.Refused(
            "${ids.size - cards.size} of those ids are not on the hire's board, so nothing was " +
                "proposed. Read read_board again and pass only the ids it gives.",
        )
        else -> Found.Ok(cards)
    }

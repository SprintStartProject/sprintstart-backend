package com.sprintstart.sprintstartbackend.onboarding.model.request.board

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import jakarta.validation.Valid

/**
 * The whole arrangement, replacing whatever was stored.
 *
 * Sent whole rather than as a patch, the same call [ReorderBoardRequest] makes: the arrangement is
 * small, the client holds all of it, and a patch language for "this card is now `LATER` and also
 * that area was renamed" would be more machinery than the thing it describes.
 */
data class SaveBoardStructureRequest(
    @field:Valid val structure: BoardStructurePayload,
)

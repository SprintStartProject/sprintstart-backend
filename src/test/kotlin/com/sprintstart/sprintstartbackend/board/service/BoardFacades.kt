package com.sprintstart.sprintstartbackend.board.service

import io.mockk.mockk

/** The real [HireBoardService] over the services a test stubs; the rest answer relaxed. */
fun hireBoardOver(
    boardService: BoardService = mockk(relaxed = true),
    boardStructureService: BoardStructureService = mockk(relaxed = true),
    openPullRequestReader: OpenPullRequestReader = mockk(relaxed = true),
) = HireBoardService(boardService, boardStructureService, openPullRequestReader)

package com.sprintstart.sprintstartbackend.ingestion.model.exceptions

import java.util.UUID

class IngestionRunNotFoundException(
    runId: UUID,
) : RuntimeException("Ingestion run with id $runId not found")

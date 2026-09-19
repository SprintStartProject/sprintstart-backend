package com.sprintstart.sprintstartbackend.onboarding.blueprint.external

data class BlueprintPhaseSeed(
    val title: String,
    val description: String,
    val aiPrompt: String,
    val graphX: Double,
    val graphY: Double,
)

object BlueprintSeedData {
    val phases = listOf(
        BlueprintPhaseSeed(
            title = "Project Overview",
            description = "Introduces the new project member to the project, its purpose, goals, stakeholders," +
                " and overall context.",
            aiPrompt =
                """
                Generate a project-specific overview for a new project member.
                
                Use the available project artifacts to explain:
                - what the project is about,
                - its primary goals,
                - the problem it solves,
                - the main stakeholders or users,
                - and any important project context a new member should understand first.
                
                Prefer concise, practical information and reference relevant project artifacts where possible.
                """.trimIndent(),
            graphX = -26.0,
            graphY = -753.0,
        ),
        BlueprintPhaseSeed(
            title = "Environment Setup",
            description = "Guides the new project member through setting up the " +
                "local development and project environment.",
            aiPrompt =
                """
                Generate a project-specific environment setup guide using the available project artifacts.
                
                Identify and explain:
                - required software and prerequisites,
                - repository setup,
                - configuration requirements,
                - how to start the application locally,
                - required external services,
                - Docker or Kubernetes setup if applicable,
                - and how to verify that the environment works.
                
                Create actionable setup instructions and reference relevant documentation or configuration files.
                """.trimIndent(),
            graphX = -890.00,
            graphY = -301.00,
        ),
        BlueprintPhaseSeed(
            title = "Meetings",
            description = "Explains the project's recurring meetings, their purpose, participants, and expectations.",
            aiPrompt =
                """
                Generate an overview of the project's recurring meetings based on available project artifacts.
                
                Explain:
                - which meetings exist,
                - their purpose,
                - typical participants,
                - their frequency,
                - what preparation is expected,
                - and what a new project member should contribute.
                
                Include links or references to relevant meeting documentation where available.
                """.trimIndent(),
            graphX = -418.00,
            graphY = -310.00,
        ),
        BlueprintPhaseSeed(
            title = "Working Agreements",
            description = "Introduces the team's collaboration rules, " +
                "communication practices, and agreed ways of working.",
            aiPrompt =
                """
                Generate a project-specific overview of the team's working agreements.
                
                Search the available project artifacts for information about:
                - collaboration practices,
                - communication channels,
                - response expectations,
                - team conventions,
                - responsibilities,
                - decision-making practices,
                - and other agreed ways of working.
                
                Summarize the rules that are most important for a new project member.
                """.trimIndent(),
            graphX = 49.00,
            graphY = -310.00,
        ),
        BlueprintPhaseSeed(
            title = "Time Tracking",
            description = "Explains how working time or project effort " +
                "is recorded and which conventions must be followed.",
            aiPrompt =
                """
                Generate a project-specific guide to time tracking.
                
                Determine from the available project artifacts:
                - whether time tracking is required,
                - which tool is used,
                - what must be tracked,
                - how entries should be categorized,
                - relevant deadlines or reporting periods,
                - and any team-specific conventions.
                
                Provide practical instructions for a new project member.
                """.trimIndent(),
            graphX = 537.00,
            graphY = -310.00,
        ),
        BlueprintPhaseSeed(
            title = "Definition of Done / Ready",
            description = "Explains the project's criteria for when work is " +
                "ready to begin and when it is considered complete.",
            aiPrompt =
                """
                Generate an explanation of the project's Definition of Ready and Definition of Done.
                
                Use project artifacts to identify:
                - when an issue or task is ready to be worked on,
                - required acceptance criteria,
                - implementation expectations,
                - testing requirements,
                - review requirements,
                - documentation requirements,
                - and the conditions under which work is considered complete.
                
                Clearly distinguish Definition of Ready from Definition of Done where both exist.
                """.trimIndent(),
            graphX = -648.00,
            graphY = -306.00,
        ),
        BlueprintPhaseSeed(
            title = "Industry Context",
            description = "Provides the industry background needed to understand the project and its environment.",
            aiPrompt =
                """
                Generate a concise introduction to the industry context relevant to this project.
                
                Use project artifacts to explain:
                - the industry the project operates in,
                - important industry concepts,
                - relevant regulations or constraints,
                - typical users or customers,
                - and industry-specific considerations that affect the project.
                
                Focus only on information that helps a new project member understand the project.
                """.trimIndent(),
            graphX = -192.00,
            graphY = -312.00,
        ),
        BlueprintPhaseSeed(
            title = "Domain Vocabulary",
            description = "Introduces important business and project-specific terminology used throughout the project.",
            aiPrompt =
                """
                Generate a glossary of important project and domain terminology.
                
                Search the available project artifacts for recurring or important:
                - business terms,
                - abbreviations,
                - project-specific terminology,
                - product terminology,
                - and domain concepts.
                
                For every relevant term, provide a concise explanation based on project context and reference supporting artifacts where possible.
                """.trimIndent(),
            graphX = 305.00,
            graphY = -308.00,
        ),
        BlueprintPhaseSeed(
            title = "Requirements & Epics",
            description = "Introduces the major requirements, epics, product goals, and current areas of work.",
            aiPrompt =
                """
                Generate an onboarding overview of the project's requirements and major epics.
                
                Use the available project artifacts to identify:
                - major product requirements,
                - important epics or initiatives,
                - current development priorities,
                - acceptance criteria where relevant,
                - and where detailed requirements are maintained.
                
                Link the explanation to relevant backlog items, specifications, or project documentation where possible.
                """.trimIndent(),
            graphX = 773.00,
            graphY = -315.00,
        ),
        BlueprintPhaseSeed(
            title = "Architecture",
            description = "Introduces the system architecture, major components, " +
                "technical boundaries, and important architectural decisions.",
            aiPrompt =
                """
                Generate a project-specific architecture overview for a new project member.
                
                Explain:
                - the overall system architecture,
                - major components or services,
                - responsibilities of those components,
                - important communication and data flows,
                - significant technologies,
                - architectural boundaries,
                - and important architecture decisions.
                
                Identify relevant ADRs and architecture documentation and reference them where possible.
                """.trimIndent(),
            graphX = 48.00,
            graphY = -140.00,
        ),
        BlueprintPhaseSeed(
            title = "Technical Debt",
            description = "Provides an overview of known technical debt, limitations," +
                " and areas that require future improvement.",
            aiPrompt =
                """
                Generate an overview of known technical debt in the project.
                
                Search available project artifacts for:
                - documented technical debt,
                - known architectural limitations,
                - temporary solutions,
                - TODOs or follow-up work,
                - relevant issues or tickets,
                - and areas considered risky or difficult to maintain.
                
                Explain enough context for a new project member to understand why these areas matter.
                """.trimIndent(),
            graphX = 51.00,
            graphY = 39.00,
        ),
        BlueprintPhaseSeed(
            title = "Deployment",
            description = "Explains how the application is built, deployed, hosted, and operated.",
            aiPrompt =
                """
                Generate a project-specific deployment overview.
                
                Use project artifacts to explain:
                - deployment environments,
                - the CI/CD pipeline,
                - build and deployment steps,
                - infrastructure or hosting,
                - relevant services,
                - configuration and secrets handling,
                - and how deployments are verified.
                
                Highlight the information that a new project member needs before interacting with the deployment process.
                """.trimIndent(),
            graphX = -197.00,
            graphY = 242.00,
        ),
        BlueprintPhaseSeed(
            title = "Release Planning",
            description = "Introduces how releases are planned, organized, scheduled," +
                " and coordinated within the project.",
            aiPrompt =
                """
                Generate an overview of the project's release planning process.
                
                Determine from available project artifacts:
                - how releases are planned,
                - how work is assigned to releases,
                - relevant release or sprint cycles,
                - how priorities are determined,
                - who participates in release planning,
                - and where the release roadmap or plans are maintained.
                
                Provide links or references to relevant planning artifacts where possible.
                """.trimIndent(),
            graphX = 322.00,
            graphY = 253.00,
        ),
        BlueprintPhaseSeed(
            title = "Guidelines",
            description = "Introduces the project's development, review, branching, " +
                "documentation, and other working guidelines.",
            aiPrompt =
                """
                Generate a consolidated overview of the project's important guidelines.
                
                Search project artifacts for:
                - coding conventions,
                - code review rules,
                - branching strategy,
                - commit conventions,
                - documentation standards,
                - testing guidelines,
                - security guidelines,
                - and other relevant engineering practices.
                
                Focus on rules a new project member needs to follow when contributing to the project.
                """.trimIndent(),
            graphX = 60.00,
            graphY = 463.00,
        ),
        BlueprintPhaseSeed(
            title = "Role-Specific Onboarding Task 1",
            description = "Provides a practical onboarding activity selected specifically " +
                "for the project member's role.",
            aiPrompt =
                """
                Generate a practical onboarding task appropriate for the project member's role.
                
                Use the user's project role and available project artifacts to select an activity that:
                - reflects realistic work performed by that role,
                - introduces important project-specific workflows,
                - can be completed safely by a new project member,
                - and helps the user interact with real project artifacts or processes.
                
                Use typical role-specific onboarding activities as guidance, but adapt the task to the actual project.
                """.trimIndent(),
            graphX = 467.00,
            graphY = 462.00,
        ),
        BlueprintPhaseSeed(
            title = "Role-Specific Onboarding Task 2",
            description = "Provides a second practical onboarding activity selected " +
                "specifically for the project member's role.",
            aiPrompt =
                """
                Generate a second practical onboarding task appropriate for the project member's role.
                
                It should complement the first role-specific onboarding task rather than duplicate it.
                
                Use the user's project role and available project artifacts to select an activity that:
                - represents realistic work for the role,
                - teaches another important project workflow,
                - can be performed safely during onboarding,
                - and connects the user with actual project processes or artifacts.
                
                Adapt the activity to the actual project rather than producing a generic exercise.
                """.trimIndent(),
            graphX = 628.00,
            graphY = 659.00,
        ),
    )
}

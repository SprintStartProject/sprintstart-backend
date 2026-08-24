package com.sprintstart.sprintstartbackend.insights

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.InsightsConfig
import com.sprintstart.sprintstartbackend.KnowledgeGapsInsightsConfig
import com.sprintstart.sprintstartbackend.UploadConfig

/**
 * An [ApplicationConfig] for insights tests, with only the insight settings worth varying exposed.
 *
 * The rest of the config is required by the type but irrelevant here, and spelling it out in every
 * test would bury the one or two limits a test is actually about.
 */
fun insightsTestConfig(
    faq: FaqInsightsConfig = FaqInsightsConfig(),
    knowledgeGaps: KnowledgeGapsInsightsConfig = KnowledgeGapsInsightsConfig(),
): ApplicationConfig = ApplicationConfig(
    ai = AiConfig(baseUrl = "http://ai.test"),
    github = GithubConfig(baseUrl = "https://github.test", cron = "0 0 * * *"),
    crypto = CryptoConfig(masterKey = "test-master-key", salt = "0123456789abcdef"),
    upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
    insights = InsightsConfig(faq = faq, knowledgeGaps = knowledgeGaps),
)

package com.sprintstart.sprintstartbackend.insights

import com.sprintstart.sprintstartbackend.shared.config.AiConfig
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.config.BitbucketConfig
import com.sprintstart.sprintstartbackend.shared.config.CryptoConfig
import com.sprintstart.sprintstartbackend.shared.config.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.shared.config.GithubConfig
import com.sprintstart.sprintstartbackend.shared.config.InsightsConfig
import com.sprintstart.sprintstartbackend.shared.config.KnowledgeGapsInsightsConfig
import com.sprintstart.sprintstartbackend.shared.config.UploadConfig

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
    github = GithubConfig(baseUrl = "https://github.test"),
    bitbucket = BitbucketConfig(baseUrl = "https://api.bitbucket.org/2.0"),
    crypto = CryptoConfig(masterKey = "test-master-key", salt = "0123456789abcdef"),
    upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
    insights = InsightsConfig(faq = faq, knowledgeGaps = knowledgeGaps),
)

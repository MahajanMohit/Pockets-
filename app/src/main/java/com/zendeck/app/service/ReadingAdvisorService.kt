package com.zendeck.app.service

import com.zendeck.app.data.memory.MemoryData
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.domain.model.ReadingRating

/**
 * Stateless service that rates a [LinkItem] based on the user's reading history.
 *
 * Rating logic (no LLM required — pure pattern matching):
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Signal                             │ Rating                            │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Domain ≥2 opens + any tag match    │ DEFINITELY_READ                   │
 * │ Tag score ≥ 2 (from any domain)    │ DEFINITELY_READ                   │
 * │ Domain ≥1 open OR tag score ≥ 1    │ GOOD_TO_READ                      │
 * │ Domain engagement ratio < 30% +    │                                   │
 * │   no tag match (≥ 3 interactions)  │ CAN_SKIP                          │
 * │ < 5 total actions in history       │ null (not enough data)            │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
object ReadingAdvisorService {

    fun rate(link: LinkItem, memory: MemoryData): ReadingRating? {
        // AI tag takes priority over heuristics
        val aiTag = link.tags.firstOrNull { it.startsWith("ai:") }?.removePrefix("ai:")
        if (aiTag != null) return when (aiTag) {
            "must"  -> ReadingRating.DEFINITELY_READ
            "skip"  -> ReadingRating.CAN_SKIP
            else    -> ReadingRating.GOOD_TO_READ
        }

        // Need at least 5 interactions before making suggestions
        val totalActions = memory.totalOpened + memory.totalSkipped
        if (totalActions < 5) return null

        val domainOpened  = memory.openedDomains[link.domain] ?: 0
        val domainSkipped = memory.skippedDomains[link.domain] ?: 0
        // Exclude internal ai: tags from topic scoring
        val tagScore = link.tags.filter { !it.startsWith("ai:") }.sumOf { memory.openedTags[it] ?: 0 }

        val domainTotal = domainOpened + domainSkipped
        val engagementRatio = if (domainTotal > 0)
            domainOpened.toFloat() / domainTotal else 0.5f  // neutral default

        return when {
            // Strong positive: familiar domain AND topic match
            domainOpened >= 2 && tagScore >= 1          -> ReadingRating.DEFINITELY_READ

            // Topic match regardless of domain
            tagScore >= 2                               -> ReadingRating.DEFINITELY_READ

            // Mostly skipped from this domain, no topic redemption
            engagementRatio < 0.3f && domainTotal >= 3 && tagScore == 0
                                                        -> ReadingRating.CAN_SKIP

            // Some positive signal
            domainOpened >= 1 || tagScore >= 1          -> ReadingRating.GOOD_TO_READ

            // Mostly skipped, weak topic match
            engagementRatio < 0.3f && domainTotal >= 3  -> ReadingRating.CAN_SKIP

            // No data on this source
            else                                        -> null
        }
    }
}

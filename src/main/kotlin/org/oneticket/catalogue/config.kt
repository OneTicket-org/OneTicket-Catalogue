package org.oneticket.catalogue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Time is an injected port ([SVC-15]); tests replace it with `Clock.fixed(...)`. */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

/**
 * Typed, validated configuration ([STY-12], [OPS-21], [OPS-22]): an invalid value stops the boot.
 * The values themselves are operational and live in `application.properties` ([OPS-26]).
 */
@ConfigurationProperties(prefix = "catalogue")
data class CatalogueProps(val pagination: Pagination) {

    data class Pagination(val defaultLimit: Int, val maxLimit: Int) {
        init {
            require(defaultLimit >= 1) { "catalogue.pagination.default-limit must be at least 1, was $defaultLimit" }
            require(maxLimit >= defaultLimit) {
                "catalogue.pagination.max-limit ($maxLimit) must not be below default-limit ($defaultLimit)"
            }
        }

        /** The page size actually served: the default when none is requested, clamped to the maximum ([API-37]). */
        fun served(requested: Int?): Int = minOf(requested ?: defaultLimit, maxLimit)
    }
}

package org.oneticket.catalogue

/** `Cache-Control` of public event and venue reads: cacheable for a few minutes ([API-31]). */
const val PUBLIC_READ_CACHE = "public, max-age=300"

/** Strong ETag carrying the row version of the resource ([API-29], contract example `"v4"`). */
fun etagOf(version: Long): String = "\"v$version\""

/** `If-None-Match` on a read: `*` or any listed entity tag, weak prefix ignored; true means answer 304 ([API-32]). */
fun matchesIfNoneMatch(header: String?, etag: String): Boolean {
    if (header == null) return false
    return header.split(",")
        .map { it.trim().removePrefix("W/") }
        .any { it == "*" || it == etag }
}

/**
 * `If-Match` on a write ([API-33]): the header is optional, so an absent one lets the write proceed;
 * a present one must be `*` or list the current strong entity tag, otherwise the answer is 412.
 */
fun ifMatchSatisfied(header: String?, etag: String): Boolean {
    if (header == null) return true
    return header.split(",")
        .map { it.trim() }
        .any { it == "*" || it == etag }
}

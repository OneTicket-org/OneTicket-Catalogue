package org.oneticket.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpHelpersTest {

    @Test
    fun `the ETag carries the row version`() {
        assertEquals("\"v4\"", etagOf(4))
    }

    @Test
    fun `If-None-Match matches a listed or wildcard entity tag`() {
        assertTrue(matchesIfNoneMatch("\"v4\"", "\"v4\""))
        assertTrue(matchesIfNoneMatch("\"v9\", W/\"v4\"", "\"v4\""))
        assertTrue(matchesIfNoneMatch("*", "\"v4\""))
        assertFalse(matchesIfNoneMatch("\"v3\"", "\"v4\""))
        assertFalse(matchesIfNoneMatch(null, "\"v4\""))
    }

    @Test
    fun `an absent If-Match lets the write proceed, a stale one does not`() {
        assertTrue(ifMatchSatisfied(null, "\"v4\""))
        assertTrue(ifMatchSatisfied("\"v4\"", "\"v4\""))
        assertTrue(ifMatchSatisfied("*", "\"v4\""))
        assertFalse(ifMatchSatisfied("\"v3\"", "\"v4\""))
    }

    @Test
    fun `a weak entity tag never satisfies If-Match`() {
        assertFalse(ifMatchSatisfied("W/\"v4\"", "\"v4\""))
    }

    @Test
    fun `the served page size is the default, clamped to the maximum`() {
        val p = CatalogueProps.Pagination(defaultLimit = 50, maxLimit = 200)
        assertEquals(50, p.served(null))
        assertEquals(10, p.served(10))
        assertEquals(200, p.served(500))
    }

    @Test
    fun `invalid pagination configuration is refused`() {
        assertFailsWith<IllegalArgumentException> { CatalogueProps.Pagination(defaultLimit = 0, maxLimit = 200) }
        assertFailsWith<IllegalArgumentException> { CatalogueProps.Pagination(defaultLimit = 300, maxLimit = 200) }
    }
}

package de.danoeh.antennapod.net.sync.service

import junit.framework.TestCase

class GuidValidatorTest : TestCase() {

    fun testIsValidGuid() {
        assertTrue(GuidValidator.isValidGuid("skfjsdvgsd"))
    }

    fun testIsInvalidGuid() {
        assertFalse(GuidValidator.isValidGuid(""))
        assertFalse(GuidValidator.isValidGuid(" "))
        assertFalse(GuidValidator.isValidGuid("\n"))
        assertFalse(GuidValidator.isValidGuid(" \n"))
        assertFalse(GuidValidator.isValidGuid(null))
        assertFalse(GuidValidator.isValidGuid("null"))
    }
}

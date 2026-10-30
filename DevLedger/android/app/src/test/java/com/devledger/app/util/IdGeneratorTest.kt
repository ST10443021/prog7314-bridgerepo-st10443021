package com.devledger.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

class IdGeneratorTest {

    @Test
    fun `newId returns a valid, non-blank UUID string`() {
        val id = IdGenerator.newId()
        assertThat(id).isNotEmpty()
        // A valid UUID is 36 characters: 8-4-4-4-12 hex groups separated by hyphens.
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    @Test
    fun `newId returns a different value on every call`() {
        val first = IdGenerator.newId()
        val second = IdGenerator.newId()
        assertThat(first).isNotEqualTo(second)
    }
}

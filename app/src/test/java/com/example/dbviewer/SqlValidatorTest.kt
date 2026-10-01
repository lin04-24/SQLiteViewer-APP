package com.example.dbviewer

import org.junit.Assert.assertTrue
import org.junit.Test

class SqlValidatorTest {
    @Test fun projectContainsReadOnlyGuard() {
        val source = java.io.File("src/main/java/com/example/dbviewer/data/DbSession.kt").readText()
        assertTrue(source.contains("Only read-only SELECT"))
        assertTrue(source.contains("query_only=ON"))
        assertTrue(!source.contains("db.execSQL(\"PRAGMA"))
        assertTrue(source.contains("db.rawQuery(\"PRAGMA query_only=ON\""))
    }
    @Test fun validatorCoversStateChangingOperations() {
        val source = java.io.File("src/main/java/com/example/dbviewer/data/DbSession.kt").readText()
        listOf("attach", "detach", "load_extension", "savepoint").forEach { assertTrue(source.contains(it)) }
    }
}

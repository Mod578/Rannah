package com.bal.reminders.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs رَنّة's real [androidx.room.migration.Migration] objects against a real
 * SQLite engine on the JVM, so migration safety is verified without a device.
 *
 * Two things make this a genuine test rather than a rehearsal:
 *
 * 1. The starting database is built from the *exported* schema JSON in
 *    `app/schemas`, not from SQL retyped into the test. A v4 database here is
 *    byte-for-byte the v4 database Room shipped.
 * 2. The migration under test is the production object itself. The
 *    [SupportSQLiteDatabase] it receives is a thin proxy that forwards
 *    `execSQL` to JDBC; nothing about the statements is reinterpreted.
 *
 * The engine is not the device's, so this proves the SQL and the data
 * transformations, not the exact binary behaviour of any one OEM's SQLite.
 */
object Schemas {

    private val schemaDir: File by lazy {
        val configured = System.getProperty("rannah.schemaDir")
            ?: error("rannah.schemaDir system property is not set; see app/build.gradle.kts")
        File(configured, "com.bal.reminders.data.db.BalDatabase").also {
            check(it.isDirectory) { "Exported Room schemas not found at ${it.absolutePath}" }
        }
    }

    private fun read(version: Int): JsonObject =
        File(schemaDir, "$version.json").reader().use { JsonParser.parseReader(it) }
            .asJsonObject.getAsJsonObject("database")

    /** Every `CREATE` statement Room exported for [version], tables then indices. */
    fun createStatements(version: Int): List<String> = buildList {
        for (entity in read(version).getAsJsonArray("entities")) {
            val table = entity.asJsonObject
            val name = table.get("tableName").asString
            add(table.get("createSql").asString.replace("\${TABLE_NAME}", name))
            table.getAsJsonArray("indices")?.forEach { index ->
                add(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", name))
            }
        }
    }

    fun identityHash(version: Int): String = read(version).get("identityHash").asString

    /** Every schema version Room has exported, ascending. */
    fun exportedVersions(): List<Int> =
        schemaDir.listFiles().orEmpty()
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            .sorted()

    /** An in-memory database holding exactly the schema Room exported for [version]. */
    fun openAt(version: Int): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").apply {
            createStatement().use { statement ->
                createStatements(version).forEach(statement::execute)
            }
        }
}

/**
 * A [SupportSQLiteDatabase] that forwards `execSQL` to [this] JDBC connection.
 *
 * Only the members رَنّة's migrations actually call are implemented; anything
 * else fails loudly rather than silently returning a default, so a future
 * migration that reaches for a query API cannot pass this harness by accident.
 */
fun Connection.asSupportDatabase(): SupportSQLiteDatabase {
    val connection = this
    return Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader,
        arrayOf(SupportSQLiteDatabase::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "execSQL" -> {
                require(args.size == 1) { "execSQL with bind arguments is not supported here" }
                connection.createStatement().use { it.execute(args[0] as String) }
                null
            }
            // Room wraps a migration in its own transaction; these are no-ops.
            "beginTransaction", "setTransactionSuccessful", "endTransaction" -> null
            "isOpen" -> true
            "getVersion" -> 0
            "toString" -> "JvmSqliteSupportDatabase"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> args[0] === proxy
            else -> error("SupportSQLiteDatabase.${method.name}() is not available in migration tests")
        }
    } as SupportSQLiteDatabase
}

/** Runs [sql] and maps every row through [row]. */
fun <T> Connection.select(sql: String, row: (java.sql.ResultSet) -> T): List<T> =
    createStatement().use { statement ->
        statement.executeQuery(sql).use { results ->
            buildList { while (results.next()) add(row(results)) }
        }
    }

fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

fun Connection.count(sql: String): Long = select(sql) { it.getLong(1) }.single()

fun Connection.tableNames(): List<String> =
    select(
        "SELECT name FROM sqlite_master WHERE type = 'table' " +
            "AND name NOT LIKE 'sqlite_%' ORDER BY name",
    ) { it.getString(1) }

/**
 * A normalised description of the physical schema: every table with its columns
 * and indices, each sorted by name.
 *
 * Column *order* is deliberately ignored. `ALTER TABLE ADD COLUMN` appends,
 * while a freshly created table places the column where the entity declares it,
 * so a migrated v5 and a fresh v5 differ in order and in nothing else, which
 * is exactly the distinction Room itself makes when it validates a schema.
 */
fun Connection.describeSchema(): String = buildString {
    for (table in tableNames()) {
        appendLine("TABLE $table")
        select("PRAGMA table_info(`$table`)") {
            "  column ${it.getString("name")} " +
                "type=${it.getString("type")} " +
                "notNull=${it.getInt("notnull")} " +
                "default=${it.getString("dflt_value")} " +
                "pk=${it.getInt("pk")}"
        }.sorted().forEach(::appendLine)

        val indices = select("PRAGMA index_list(`$table`)") {
            Triple(it.getString("name"), it.getInt("unique"), it.getString("origin"))
        }.sortedBy { it.first }
        for ((name, unique, origin) in indices) {
            val columns = select("PRAGMA index_info(`$name`)") {
                it.getInt("seqno") to it.getString("name")
            }.sortedBy { it.first }.joinToString(",") { it.second }
            appendLine("  index $name unique=$unique origin=$origin on ($columns)")
        }
    }
}

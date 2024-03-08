/*
 * Copyright 2017 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.database

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.util.SparseArray

/*
 * Created by Hippo on 2017/9/3.
 */   class MSQLiteBuilder {
    private var version = 0
    private var statements: MutableList<String>? = null
    private val statementsMap = SparseArray<List<String>?>()

    /**
     * Bump database version.
     */
    fun version(version: Int): MSQLiteBuilder {
        check(version > this.version) {
            ("New version must be bigger than current version. "
                    + "current version: " + this.version + ", new version: " + version + ".")
        }
        this.version = version
        statements = ArrayList()
        statementsMap.put(version, statements)
        return this
    }
    /**
     * Creates a table.
     */
    /**
     * Creates a table with int [.COLUMN_ID] primary key.
     */
    @JvmOverloads
    fun createTable(
        table: String,
        column: String = COLUMN_ID,
        clazz: Class<*>? = Int::class.javaPrimitiveType
    ): MSQLiteBuilder {
        return statement("CREATE TABLE " + table + " (" + column + " " + javaTypeToSQLiteType(clazz) + " PRIMARY KEY);")
    }

    /**
     * Drops a table.
     */
    fun dropTable(table: String): MSQLiteBuilder {
        return statement("DROP TABLE $table;")
    }

    /**
     * Inserts a column to the table.
     */
    fun insertColumn(table: String, column: String, clazz: Class<*>?): MSQLiteBuilder {
        return statement(
            "ALTER TABLE " + table + " ADD COLUMN " + column + " " + javaTypeToSQLiteType(
                clazz
            ) + ";"
        )
    }

    /**
     * Add a statement.
     */
    fun statement(statement: String): MSQLiteBuilder {
        check(!(version == 0 || statements == null)) { "Call version() first!" }
        statements!!.add(statement)
        return this
    }

    /**
     * Build a SQLiteOpenHelper from it.
     */
    fun build(context: Context?, name: String?, version: Int): SQLiteOpenHelper {
        return MSQLiteOpenHelper(context, name, version, this)
    }

    fun getStatements(oldVersion: Int, newVersion: Int): List<String> {
        val result: MutableList<String> = ArrayList()
        for (i in oldVersion + 1..newVersion) {
            val list = statementsMap[i]
            if (list != null) {
                result.addAll(list)
            }
        }
        return result
    }

    companion object {
        const val COLUMN_ID = "_id"
        private val JAVA_TYPE_TO_SQLITE_TYPE: MutableMap<Class<*>?, String> = HashMap()

        init {
            JAVA_TYPE_TO_SQLITE_TYPE[Boolean::class.javaPrimitiveType] =
                "INTEGER NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[Byte::class.javaPrimitiveType] = "INTEGER NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[Short::class.javaPrimitiveType] = "INTEGER NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[Int::class.javaPrimitiveType] = "INTEGER NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[Long::class.javaPrimitiveType] = "INTEGER NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[Float::class.javaPrimitiveType] = "REAL NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[Double::class.javaPrimitiveType] = "REAL NOT NULL DEFAULT 0"
            JAVA_TYPE_TO_SQLITE_TYPE[String::class.java] = "TEXT"
        }

        private fun javaTypeToSQLiteType(clazz: Class<*>?): String {
            return JAVA_TYPE_TO_SQLITE_TYPE[clazz]
                ?: throw IllegalStateException("Unknown type: $clazz")
        }
    }
}
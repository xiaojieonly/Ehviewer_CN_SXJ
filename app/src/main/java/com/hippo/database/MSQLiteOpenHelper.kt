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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hippo.util.SqlUtils

/*
 * Created by Hippo on 2017/9/4.
 */internal class MSQLiteOpenHelper(
    context: Context?,
    name: String?,
    private val version: Int,
    private val builder: MSQLiteBuilder
) : SQLiteOpenHelper(context, name, null, version) {
    override fun onCreate(db: SQLiteDatabase) {
        onUpgrade(db, 0, version)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (command in builder.getStatements(oldVersion, newVersion)) {
            db.execSQL(command)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        SqlUtils.dropAllTable(db)
        onCreate(db)
    }
}
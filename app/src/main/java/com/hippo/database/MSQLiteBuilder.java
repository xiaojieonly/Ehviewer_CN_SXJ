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

package com.hippo.database;

/*
 * Created by Hippo on 2017/9/3.
 */

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.SparseArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MSQLiteBuilder {

  public static final String COLUMN_ID = "_id";

  private static final Map<Class, String> JAVA_TYPE_TO_SQLITE_TYPE = new HashMap<>();

  static {
    JAVA_TYPE_TO_SQLITE_TYPE.put(boolean.class, "INTEGER NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(byte.class, "INTEGER NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(short.class, "INTEGER NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(int.class, "INTEGER NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(long.class, "INTEGER NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(float.class, "REAL NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(double.class, "REAL NOT NULL DEFAULT 0");
    JAVA_TYPE_TO_SQLITE_TYPE.put(String.class, "TEXT");
  }

  private static String javaTypeToSQLiteType(Class clazz) {
    String type = JAVA_TYPE_TO_SQLITE_TYPE.get(clazz);
    if (type == null) {
      throw new IllegalStateException("Unknown type: " + clazz);
    }
    return type;
  }

  private int version = 0;
  private List<String> statements;
  private SparseArray<List<String>> statementsMap = new SparseArray<>();

  /**
   * Bump database version.
   */
  public MSQLiteBuilder version(int version) {
    if (version <= this.version) {
      throw new IllegalStateException("New version must be bigger than current version. "
          + "current version: " + this.version + ", new version: " + version + ".");
    }
    this.version = version;
    this.statements = new ArrayList<>();
    this.statementsMap.put(version, this.statements);
    return this;
  }

  /**
   * Creates a table with int {@link #COLUMN_ID} primary key.
   */
  public MSQLiteBuilder createTable(String table) {
    return createTable(table, COLUMN_ID, int.class);
  }

  /**
   * Creates a table.
   */
  public MSQLiteBuilder createTable(String table, String column, Class clazz) {
    return statement("CREATE TABLE " + table + " (" + column + " " + javaTypeToSQLiteType(clazz) + " PRIMARY KEY);");
  }

  /**
   * Drops a table.
   */
  public MSQLiteBuilder dropTable(String table) {
    return statement("DROP TABLE " + table + ";");
  }

  /**
   * Inserts a column to the table.
   */
  public MSQLiteBuilder insertColumn(String table, String column, Class clazz) {
    return statement("ALTER TABLE " + table + " ADD COLUMN " + column + " " + javaTypeToSQLiteType(clazz) + ";");
  }

  /**
   * Add a statement.
   */
  public MSQLiteBuilder statement(String statement) {
    if (version == 0 || statements == null) {
      throw new IllegalStateException("Call version() first!");
    }
    statements.add(statement);
    return this;
  }

  /**
   * Build a SQLiteOpenHelper from it.
   */
  public SQLiteOpenHelper build(Context context, String name, int version) {
    return new MSQLiteOpenHelper(context, name, version, this);
  }

  List<String> getStatements(int oldVersion, int newVersion) {
    List<String> result = new ArrayList<>();
    for (int i = oldVersion + 1; i <= newVersion; ++i) {
      List<String> list = statementsMap.get(i);
      if (list != null) {
        result.addAll(list);
      }
    }
    return result;
  }
}

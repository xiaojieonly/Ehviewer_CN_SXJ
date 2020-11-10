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
 * Created by Hippo on 2017/9/4.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.hippo.util.SqlUtils;

class MSQLiteOpenHelper extends SQLiteOpenHelper {

  private final int version;
  private final MSQLiteBuilder builder;

  public MSQLiteOpenHelper(Context context, String name, int version, MSQLiteBuilder builder) {
    super(context, name, null, version);
    this.version = version;
    this.builder = builder;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    onUpgrade(db, 0, version);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    for (String command : builder.getStatements(oldVersion, newVersion)) {
      db.execSQL(command);
    }
  }

  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    SqlUtils.dropAllTable(db);
    onCreate(db);
  }
}

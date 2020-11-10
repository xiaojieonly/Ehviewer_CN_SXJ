/*
 * Copyright 2018 Hippo Seven
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

package com.hippo.ehviewer;

/*
 * Created by Hippo on 2018/3/21.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.hippo.database.MSQLiteBuilder;
import com.hippo.util.SqlUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Hosts {

  private static final int VERSION_1 = 1;
  private static final String TABLE_HOSTS = "HOSTS";
  private static final String COLUMN_HOST = "HOST";
  private static final String COLUMN_IP = "IP";

  private static final int DB_VERSION = VERSION_1;

  private final SQLiteOpenHelper helper;
  private final SQLiteDatabase db;

  public Hosts(Context context, String name) {
    helper = new MSQLiteBuilder()
        .version(VERSION_1)
        .createTable(TABLE_HOSTS)
        .insertColumn(TABLE_HOSTS, COLUMN_HOST, String.class)
        .insertColumn(TABLE_HOSTS, COLUMN_IP, String.class)
        .build(context, name, DB_VERSION);
    db = helper.getWritableDatabase();
  }

  /**
   * Gets a InetAddress with the host.
   */
  @Nullable
  public InetAddress get(String host) {
    if (!isValidHost(host)) {
      return null;
    }

    Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_HOSTS + " WHERE " + COLUMN_HOST  + " = ?;", new String[] {host});
    try {
      if (cursor.moveToNext()) {
        String ip = SqlUtils.getString(cursor, COLUMN_IP, null);
        return toInetAddress(host, ip);
      } else {
        return null;
      }
    } finally {
      cursor.close();
    }
  }

  private boolean contains(String host) {
    Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_HOSTS + " WHERE " + COLUMN_HOST  + " = ?;", new String[] {host});
    try {
      return cursor.moveToNext();
    } finally {
      cursor.close();
    }
  }

  /**
   * Puts the host-ip pair into this hosts.
   */
  public boolean put(String host, String ip) {
    if (!isValidHost(host) || !isValidIp(ip)) {
      return false;
    }

    ContentValues values = new ContentValues();
    values.put(COLUMN_HOST, host);
    values.put(COLUMN_IP, ip);

    if (contains(host)) {
      db.update(TABLE_HOSTS, values, COLUMN_HOST + " = ?", new String[] { host });
    } else {
      db.insert(TABLE_HOSTS, null, values);
    }

    return true;
  }

  /**
   * Puts delete the entry with the host.
   */
  public void delete(String host) {
    db.delete(TABLE_HOSTS, COLUMN_HOST + " = ?", new String[] { host });
  }

  /**
   * Get all data from this host.
   */
  public List<Pair<String, String>> getAll() {
    List<Pair<String, String>> result = new ArrayList<>();

    Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_HOSTS + ";", null);
    try {
      while (cursor.moveToNext()) {
        String host = SqlUtils.getString(cursor, COLUMN_HOST, null);
        String ip = SqlUtils.getString(cursor, COLUMN_IP, null);

        InetAddress inetAddress = toInetAddress(host, ip);
        if (inetAddress == null) {
          continue;
        }

        result.add(new Pair<>(host, ip));
      }
    } finally {
      cursor.close();
    }

    return result;
  }

  @Nullable
  public static InetAddress toInetAddress(String host, String ip) {
    if (!isValidHost(host)) {
      return null;
    }

    if (ip == null) {
      return null;
    }

    byte[] bytes = parseV4(ip);
    if (bytes == null) {
      bytes = parseV6(ip);
    }
    if (bytes == null) {
      return null;
    }

    try {
      return InetAddress.getByAddress(host, bytes);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  /**
   * Returns true if the host is valid.
   */
  public static boolean isValidHost(String host) {
    if (host == null) {
      return false;
    }

    if (host.length() > 253) {
      return false;
    }

    int labelLength = 0;
    for (int i = 0, n = host.length(); i < n; i++) {
      char ch = host.charAt(i);

      if (ch == '.') {
        if (labelLength < 1 || labelLength > 63) {
          return false;
        }
        labelLength = 0;
      } else {
        labelLength++;
      }

      if ((ch < 'a' || ch > 'z') && (ch < '0' || ch > '9') && ch != '-' && ch != '.') {
        return false;
      }
    }

    if (labelLength < 1 || labelLength > 63) {
      return false;
    }

    return true;
  }

  /**
   * Returns true if the ip is valid.
   */
  public static boolean isValidIp(String ip) {
    return ip != null && (parseV4(ip) != null || parseV6(ip) != null);
  }

  // org.xbill.DNS.Address.parseV4
  @Nullable
  private static byte[] parseV4(String s) {
    int numDigits;
    int currentOctet;
    byte [] values = new byte[4];
    int currentValue;
    int length = s.length();

    currentOctet = 0;
    currentValue = 0;
    numDigits = 0;
    for (int i = 0; i < length; i++) {
      char c = s.charAt(i);
      if (c >= '0' && c <= '9') {
        /* Can't have more than 3 digits per octet. */
        if (numDigits == 3)
          return null;
        /* Octets shouldn't start with 0, unless they are 0. */
        if (numDigits > 0 && currentValue == 0)
          return null;
        numDigits++;
        currentValue *= 10;
        currentValue += (c - '0');
        /* 255 is the maximum value for an octet. */
        if (currentValue > 255)
          return null;
      } else if (c == '.') {
        /* Can't have more than 3 dots. */
        if (currentOctet == 3)
          return null;
        /* Two consecutive dots are bad. */
        if (numDigits == 0)
          return null;
        values[currentOctet++] = (byte) currentValue;
        currentValue = 0;
        numDigits = 0;
      } else
        return null;
    }
    /* Must have 4 octets. */
    if (currentOctet != 3)
      return null;
    /* The fourth octet can't be empty. */
    if (numDigits == 0)
      return null;
    values[currentOctet] = (byte) currentValue;
    return values;
  }

  // org.xbill.DNS.Address.parseV6
  @Nullable
  private static byte[] parseV6(String s) {
    int range = -1;
    byte [] data = new byte[16];

    String [] tokens = s.split(":", -1);

    int first = 0;
    int last = tokens.length - 1;

    if (tokens[0].length() == 0) {
      // If the first two tokens are empty, it means the string
      // started with ::, which is fine.  If only the first is
      // empty, the string started with :, which is bad.
      if (last - first > 0 && tokens[1].length() == 0)
        first++;
      else
        return null;
    }

    if (tokens[last].length() == 0) {
      // If the last two tokens are empty, it means the string
      // ended with ::, which is fine.  If only the last is
      // empty, the string ended with :, which is bad.
      if (last - first > 0 && tokens[last - 1].length() == 0)
        last--;
      else
        return null;
    }

    if (last - first + 1 > 8)
      return null;

    int i, j;
    for (i = first, j = 0; i <= last; i++) {
      if (tokens[i].length() == 0) {
        if (range >= 0)
          return null;
        range = j;
        continue;
      }

      if (tokens[i].indexOf('.') >= 0) {
        // An IPv4 address must be the last component
        if (i < last)
          return null;
        // There can't have been more than 6 components.
        if (i > 6)
          return null;
        byte [] v4addr = parseV4(tokens[i]);
        if (v4addr == null)
          return null;
        for (int k = 0; k < 4; k++)
          data[j++] = v4addr[k];
        break;
      }

      try {
        for (int k = 0; k < tokens[i].length(); k++) {
          char c = tokens[i].charAt(k);
          if (Character.digit(c, 16) < 0)
            return null;
        }
        int x = Integer.parseInt(tokens[i], 16);
        if (x > 0xFFFF || x < 0)
          return null;
        data[j++] = (byte)(x >>> 8);
        data[j++] = (byte)(x & 0xFF);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }

    if (j < 16 && range < 0)
      return null;

    if (range >= 0) {
      int empty = 16 - j;
      System.arraycopy(data, range, data, range + empty, j - range);
      for (i = range; i < range + empty; i++)
        data[i] = 0;
    }

    return data;
  }
}

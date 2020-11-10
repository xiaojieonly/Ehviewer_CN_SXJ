/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import com.hippo.ehviewer.R;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@SuppressLint("SimpleDateFormat")
public final class ReadableTime {

    private static Resources sResources;

    public static final long SECOND_MILLIS = 1000;
    public static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    public static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    public static final long DAY_MILLIS = 24 * HOUR_MILLIS;
    public static final long WEEK_MILLIS = 7 * DAY_MILLIS;
    public static final long YEAR_MILLIS = 365 * DAY_MILLIS;

    public static final int SIZE = 5;

    public static final long[] MULTIPLES = {
            YEAR_MILLIS,
            DAY_MILLIS,
            HOUR_MILLIS,
            MINUTE_MILLIS,
            SECOND_MILLIS
    };

    public static final int[] UNITS = {
            R.plurals.year,
            R.plurals.day,
            R.plurals.hour,
            R.plurals.minute,
            R.plurals.second
    };

    private static final Calendar sCalendar = Calendar.getInstance();
    private static final Object sCalendarLock = new Object();

    private static final SimpleDateFormat DATE_FORMAT_WITHOUT_YEAR = new SimpleDateFormat("MMM d");
    private static final SimpleDateFormat DATE_FORMAT_WITH_YEAR = new SimpleDateFormat("MMM d, yyyy");

    private static final SimpleDateFormat DATE_FORMAT_WITHOUT_YEAR_ZH = new SimpleDateFormat("M月d日");
    private static final SimpleDateFormat DATE_FORMAT_WITH_YEAR_ZH = new SimpleDateFormat("yyyy年M月d日");

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy-MM-dd HH:mm");
    private static final Object sDateFormatLock1 = new Object();

    private static final SimpleDateFormat FILENAMABLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
    private static final Object sDateFormatLock2 = new Object();

    public static void initialize(Context context) {
        sResources = context.getApplicationContext().getResources();
    }

    /*
    public static String getDisplayTime(long time) {
        if (Settings.getPrettyTime()) {
            return getTimeAgo(time);
        } else {
            return getPlainTime(time);
        }
    }
    */

    public static String getPlainTime(long time) {
        synchronized (sDateFormatLock1) {
            return DATE_FORMAT.format(new Date(time));
        }
    }

    public static String getTimeAgo(long time) {
        Resources resources = sResources;

        long now = System.currentTimeMillis();
        if (time > now + (2 * MINUTE_MILLIS) || time <= 0) {
            return resources.getString(R.string.from_the_future);
        }

        final long diff = now - time;
        if (diff < MINUTE_MILLIS) {
            return resources.getString(R.string.just_now);
        } else if (diff < 2 * MINUTE_MILLIS) {
            return resources.getQuantityString(R.plurals.some_minutes_ago, 1, 1);
        } else if (diff < 50 * MINUTE_MILLIS) {
            int minutes = (int) (diff / MINUTE_MILLIS);
            return resources.getQuantityString(R.plurals.some_minutes_ago, minutes, minutes);
        } else if (diff < 90 * MINUTE_MILLIS) {
            return resources.getQuantityString(R.plurals.some_hours_ago, 1, 1);
        } else if (diff < 24 * HOUR_MILLIS) {
            int hours = (int) (diff / HOUR_MILLIS);
            return resources.getQuantityString(R.plurals.some_hours_ago, hours, hours);
        } else if (diff < 48 * HOUR_MILLIS) {
            return resources.getString(R.string.yesterday);
        } else if (diff < WEEK_MILLIS) {
            int days = (int) (diff / DAY_MILLIS);
            return resources.getString(R.string.some_days_ago, days);
        } else {
            synchronized (sCalendarLock) {
                Date nowDate = new Date(now);
                Date timeDate = new Date(time);
                sCalendar.setTime(nowDate);
                int nowYear = sCalendar.get(Calendar.YEAR);
                sCalendar.setTime(timeDate);
                int timeYear = sCalendar.get(Calendar.YEAR);
                boolean isZh = Locale.getDefault().getLanguage().equals("zh");

                if (nowYear == timeYear) {
                    return (isZh ? DATE_FORMAT_WITHOUT_YEAR_ZH : DATE_FORMAT_WITHOUT_YEAR).format(timeDate);
                } else {
                    return (isZh ? DATE_FORMAT_WITH_YEAR_ZH : DATE_FORMAT_WITH_YEAR).format(timeDate);
                }
            }
        }
    }

    public static String getTimeInterval(long time) {
        StringBuilder sb = new StringBuilder();
        Resources resources = sResources;

        long leftover = time;
        boolean start = false;

        for (int i = 0; i < SIZE; i++) {
            long multiple = MULTIPLES[i];
            long quotient = leftover / multiple;
            long remainder = leftover % multiple;
            if (start || quotient != 0 || i == SIZE - 1) {
                if (start) {
                    sb.append(" ");
                }
                sb.append(quotient)
                        .append(" ")
                        .append(resources.getQuantityString(UNITS[i], (int) quotient));
                start = true;
            }
            leftover = remainder;
        }

        return sb.toString();
    }

    public static String getShortTimeInterval(long time) {
        StringBuilder sb = new StringBuilder();
        Resources resources = sResources;

        for (int i = 0; i < SIZE; i++) {
            long multiple = MULTIPLES[i];
            long quotient = time / multiple;
            if (time > multiple * 1.5 || i == SIZE - 1) {
                sb.append(quotient)
                        .append(" ")
                        .append(resources.getQuantityString(UNITS[i], (int) quotient));
                break;
            }
        }

        return sb.toString();
    }

    public static String getFilenamableTime(long time) {
        synchronized (sDateFormatLock2) {
            return FILENAMABLE_DATE_FORMAT.format(new Date(time));
        }
    }
}

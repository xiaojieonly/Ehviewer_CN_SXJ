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

package com.hippo.lib.yorozuya;

import java.util.concurrent.atomic.AtomicLong;

public final class LongIdGenerator {

    public static final long INVALID_ID = -1L;

    private final AtomicLong mId = new AtomicLong();

    public LongIdGenerator() {
    }

    public LongIdGenerator(long init) {
        setNextId(init);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public long nextId() {
        long id;
        while ((id = mId.getAndIncrement()) == INVALID_ID) ;
        return id;
    }

    public void setNextId(long id) {
        checkInValidId(id);
        mId.set(id);
    }

    private void checkInValidId(long id) {
        if (INVALID_ID == id) {
            throw new IllegalStateException("Can't set INVALID_ID");
        }
    }
}

/*
 * Copyright 2019 Hippo Seven
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

package com.hippo.ehviewer.gallery;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class Pipe {

  private final int capacity;
  private final byte[] buffer;

  private int head = 0;
  private int tail = 0;
  private boolean full = false;

  private boolean inClosed = false;
  private boolean outClosed = false;

  private InputStream inputStream = new InputStream() {
    @Override
    public int read() throws IOException {
      synchronized (Pipe.this) {
        byte[] bytes = new byte[1];
        if (read(bytes, 0, 1) != -1) {
          return bytes[0];
        } else {
          return -1;
        }
      }
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
      synchronized (Pipe.this) {
        for (;;) {
          if (inClosed) {
            throw new IOException("The InputStream is closed");
          }
          if (len == 0) {
            return 0;
          }

          if (head == tail && !full) {
            if (outClosed) {
              // No bytes available and the OutputStream is closed. So it's the end.
              return -1;
            } else {
              // Wait for OutputStream write bytes
              try {
                Pipe.this.wait();
              } catch (InterruptedException e) {
                throw new IOException("The thread interrupted", e);
              }
            }
          } else {
            int read = Math.min(len, (head < tail ? tail : capacity) - head);
            System.arraycopy(buffer, head, b, off, read);
            head += read;
            if (head == capacity) {
              head = 0;
            }
            full = false;
            Pipe.this.notifyAll();
            return read;
          }
        }
      }
    }

    @Override
    public void close() {
      synchronized (Pipe.this) {
        inClosed = true;
        Pipe.this.notifyAll();
      }
    }
  };

  private OutputStream outputStream = new OutputStream() {
    @Override
    public void write(int b) throws IOException {
      synchronized (Pipe.this) {
        byte[] bytes = new byte[] { (byte) b};
        write(bytes, 0, 1);
      }
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
      synchronized (Pipe.this) {
        while (len != 0) {
          if (outClosed) {
            throw new IOException("The OutputStream is closed");
          }
          if (inClosed) {
            throw new IOException("The InputStream is closed");
          }

          if (head == tail && full) {
            // The buffer is full, wait for InputStream read bytes
            try {
              Pipe.this.wait();
            } catch (InterruptedException e) {
              throw new IOException("The thread interrupted", e);
            }
          } else {
            int write = Math.min(len, (head <= tail ? capacity : head) - tail);
            System.arraycopy(b, off, buffer, tail, write);
            off += write;
            len -= write;
            tail += write;
            if (tail == capacity) {
              tail = 0;
            }
            if (head == tail) {
              full = true;
            }
            Pipe.this.notifyAll();
          }
        }
      }
    }

    @Override
    public void close() {
      synchronized (Pipe.this) {
        outClosed = true;
        Pipe.this.notifyAll();
      }
    }
  };

  Pipe(int capacity) {
    this.capacity = capacity;
    this.buffer = new byte[capacity];
  }

  InputStream getInputStream() {
    return inputStream;
  }

  OutputStream getOutputStream() {
    return outputStream;
  }
}

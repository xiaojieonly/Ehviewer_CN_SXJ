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

import com.hippo.a7zip.ArchiveException;
import com.hippo.a7zip.InArchive;
import com.hippo.a7zip.InStream;
import com.hippo.a7zip.PropID;
import com.hippo.a7zip.PropType;
import com.hippo.a7zip.SequentialOutStream;
import com.hippo.unifile.UniRandomAccessFile;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

class A7ZipArchive implements Closeable {

  private InArchive archive;

  private A7ZipArchive(InArchive archive) {
    this.archive = archive;
  }

  @Override
  public void close() {
    archive.close();
  }

  private static boolean isSupportedFilename(String name) {
    for (String extension : GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS) {
      if (name.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  List<A7ZipArchiveEntry> getArchiveEntries() {
    List<A7ZipArchiveEntry> entries = new ArrayList<>();

    for (int i = 0, n = archive.getNumberOfEntries(); i < n; i++) {
      if (!archive.getEntryBooleanProperty(i, PropID.ENCRYPTED)
          && !archive.getEntryBooleanProperty(i, PropID.IS_DIR)
          && !archive.getEntryBooleanProperty(i, PropID.IS_VOLUME)
          && !archive.getEntryBooleanProperty(i, PropID.SOLID)) {
        String path = archive.getEntryPath(i);
        if (isSupportedFilename(path.toLowerCase())) {
          entries.add(new A7ZipArchiveEntry(archive, i, path));
        }
      }
    }

    return entries;
  }

  static A7ZipArchive create(UniRandomAccessFile file) throws ArchiveException {
    InStream store = new UniRandomAccessFileInStream(file);
    InArchive archive = InArchive.open(store);
    if ((archive.getArchivePropertyType(PropID.ENCRYPTED) == PropType.BOOL && archive.getArchiveBooleanProperty(PropID.ENCRYPTED))
        || (archive.getArchivePropertyType(PropID.SOLID) == PropType.BOOL && archive.getArchiveBooleanProperty(PropID.SOLID))
        || (archive.getArchivePropertyType(PropID.IS_VOLUME) == PropType.BOOL && archive.getArchiveBooleanProperty(PropID.IS_VOLUME))) {
      throw new ArchiveException("Unsupported archive");
    }
    return new A7ZipArchive(archive);
  }

  static class A7ZipArchiveEntry {

    private InArchive archive;
    private int index;
    private String path;

    private A7ZipArchiveEntry(InArchive archive, int index, String path) {
      this.archive = archive;
      this.index = index;
      this.path = path;
    }

    String getPath() {
      return path;
    }

    void extract(OutputStream os) throws ArchiveException {
      archive.extractEntry(index, new OutputStreamSequentialOutStream(os));
    }
  }

  private static class UniRandomAccessFileInStream implements InStream {

    private UniRandomAccessFile file;

    public UniRandomAccessFileInStream(UniRandomAccessFile file) {
      this.file = file;
    }

    @Override
    public void seek(long pos) throws IOException {
      file.seek(pos);
    }

    @Override
    public long tell() throws IOException {
      return file.getFilePointer();
    }

    @Override
    public long size() throws IOException {
      return file.length();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return file.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
      file.close();
    }
  }

  private static class OutputStreamSequentialOutStream implements SequentialOutStream {

    private OutputStream stream;

    public OutputStreamSequentialOutStream(OutputStream stream) {
      this.stream = stream;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      stream.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }
}

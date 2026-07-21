package com.hippo.ehviewer.client.wifi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class WiFiFrameCodec {

    public static final byte[] MAGIC = {'E', 'H', 'B', 'F'};
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 22;

    public static final int FLAG_LAST_CHUNK = 1;

    private WiFiFrameCodec() {
    }

    @NonNull
    public static WiFiFrame encode(@NonNull WiFiDataHand hand) {
        byte[] payload = hand.toJsonObject().toJSONString().getBytes(StandardCharsets.UTF_8);
        return new WiFiFrame(
                hand.messageType,
                hand.dataType,
                (int) hand.pageIndex,
                (int) hand.pageSize,
                0,
                payload);
    }

    @NonNull
    public static WiFiDataHand decodeDataHand(@NonNull WiFiFrame frame) {
        if (frame.payload == null || frame.payload.length == 0) {
            return new WiFiDataHand(WiFiDataHand.ERROR);
        }
        JSONObject object = JSONObject.parseObject(
                new String(frame.payload, StandardCharsets.UTF_8));
        WiFiDataHand hand = new WiFiDataHand(frame.messageType, object.getJSONObject("data"));
        hand.dataType = frame.dataType;
        hand.pageSize = frame.pageSize;
        hand.pageIndex = frame.pageIndex;
        if (object.containsKey("messageType")) {
            hand.messageType = object.getIntValue("messageType");
        }
        if (object.containsKey("dataType")) {
            hand.dataType = object.getIntValue("dataType");
        }
        if (object.containsKey("totalSize")) {
            hand.pageSize = object.getLongValue("totalSize");
        }
        if (object.containsKey("part")) {
            hand.pageIndex = object.getLongValue("part");
        }
        if (object.containsKey("data")) {
            hand.setData(object.getJSONObject("data"));
        }
        return hand;
    }

    @NonNull
    public static WiFiFrame encodeManifest(long gid, @NonNull String dirname,
            @NonNull List<WiFiDownloadMigrationHelper.FileMeta> files,
            int pageIndex, int pageSize) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        try {
            out.writeLong(gid);
            writeString(out, dirname);
            out.writeShort(Math.min(files.size(), 65535));
            for (WiFiDownloadMigrationHelper.FileMeta meta : files) {
                writeString(out, meta.name);
                writeString(out, meta.md5);
                out.writeLong(meta.size);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return new WiFiFrame(
                WiFiDataHand.SEND,
                ConnectThread.DATA_TYPE_DOWNLOAD_DIR_MANIFEST,
                pageIndex,
                pageSize,
                0,
                bos.toByteArray());
    }

    @Nullable
    public static WiFiDownloadMigrationHelper.DirManifest decodeManifest(@NonNull WiFiFrame frame) {
        if (frame.payload == null) {
            return null;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame.payload));
        try {
            long gid = in.readLong();
            String dirname = readString(in);
            int count = in.readUnsignedShort();
            List<WiFiDownloadMigrationHelper.FileMeta> files = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                WiFiDownloadMigrationHelper.FileMeta meta =
                        new WiFiDownloadMigrationHelper.FileMeta();
                meta.name = readString(in);
                meta.md5 = readString(in);
                meta.size = in.readLong();
                files.add(meta);
            }
            WiFiDownloadMigrationHelper.DirManifest manifest =
                    new WiFiDownloadMigrationHelper.DirManifest();
            manifest.gid = gid;
            manifest.dirname = dirname;
            manifest.files = files;
            manifest.pageIndex = frame.pageIndex;
            manifest.pageSize = frame.pageSize;
            return manifest;
        } catch (IOException e) {
            return null;
        }
    }

    @NonNull
    public static WiFiFrame encodeAck(long gid, @NonNull String dirname,
            @NonNull List<String> filesNeeded) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        try {
            out.writeLong(gid);
            writeString(out, dirname);
            out.writeShort(Math.min(filesNeeded.size(), 65535));
            for (String name : filesNeeded) {
                writeString(out, name);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return new WiFiFrame(
                WiFiDataHand.RECEIVED,
                ConnectThread.DATA_TYPE_DOWNLOAD_DIR_ACK,
                0,
                0,
                0,
                bos.toByteArray());
    }

    @Nullable
    public static WiFiDownloadMigrationHelper.DirAck decodeAck(@NonNull WiFiFrame frame) {
        if (frame.payload == null) {
            return null;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame.payload));
        try {
            WiFiDownloadMigrationHelper.DirAck ack = new WiFiDownloadMigrationHelper.DirAck();
            ack.gid = in.readLong();
            ack.dirname = readString(in);
            int count = in.readUnsignedShort();
            ack.filesNeeded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ack.filesNeeded.add(readString(in));
            }
            return ack;
        } catch (IOException e) {
            return null;
        }
    }

    @NonNull
    public static WiFiFrame encodeFileChunk(long gid, @NonNull String name, @NonNull String fileMd5,
            int chunkIndex, int chunkTotal, @NonNull byte[] data, boolean lastChunk) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        try {
            out.writeLong(gid);
            writeString(out, name);
            writeString(out, fileMd5);
            out.writeInt(chunkIndex);
            out.writeInt(chunkTotal);
            out.writeInt(data.length);
            out.write(data);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        int flags = lastChunk ? FLAG_LAST_CHUNK : 0;
        return new WiFiFrame(
                WiFiDataHand.SEND,
                ConnectThread.DATA_TYPE_DOWNLOAD_DIR_FILE,
                chunkIndex,
                chunkTotal,
                flags,
                bos.toByteArray());
    }

    @Nullable
    public static WiFiDownloadMigrationHelper.FileChunk decodeFileChunk(@NonNull WiFiFrame frame) {
        if (frame.payload == null) {
            return null;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame.payload));
        try {
            WiFiDownloadMigrationHelper.FileChunk chunk =
                    new WiFiDownloadMigrationHelper.FileChunk();
            chunk.gid = in.readLong();
            chunk.name = readString(in);
            chunk.fileMd5 = readString(in);
            chunk.chunkIndex = in.readInt();
            chunk.chunkTotal = in.readInt();
            int len = in.readInt();
            if (len < 0) {
                return null;
            }
            chunk.data = new byte[len];
            in.readFully(chunk.data);
            chunk.lastChunk = (frame.flags & FLAG_LAST_CHUNK) != 0;
            return chunk;
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeFrame(@NonNull OutputStream outputStream, @NonNull WiFiFrame frame)
            throws IOException {
        byte[] payload = frame.payload != null ? frame.payload : new byte[0];
        DataOutputStream out = new DataOutputStream(outputStream);
        out.write(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(frame.messageType);
        out.writeShort(frame.dataType);
        out.writeInt(frame.pageIndex);
        out.writeInt(frame.pageSize);
        out.writeShort(frame.flags);
        out.writeInt(payload.length);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    @Nullable
    public static WiFiFrame readFrame(@NonNull InputStream inputStream) throws IOException {
        byte[] header = readFully(inputStream, HEADER_SIZE);
        if (!matchesMagic(header)) {
            throw new IOException("Invalid frame magic");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(header));
        in.readFully(MAGIC);
        int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("Unsupported frame version: " + version);
        }
        WiFiFrame frame = new WiFiFrame();
        frame.messageType = in.readUnsignedByte();
        frame.dataType = in.readUnsignedShort();
        frame.pageIndex = in.readInt();
        frame.pageSize = in.readInt();
        frame.flags = in.readUnsignedShort();
        int payloadLength = in.readInt();
        if (payloadLength < 0) {
            throw new IOException("Invalid payload length");
        }
        if (payloadLength == 0) {
            frame.payload = new byte[0];
            return frame;
        }
        frame.payload = readFully(inputStream, payloadLength);
        return frame;
    }

    private static boolean matchesMagic(byte[] header) {
        if (header.length < 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (header[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static byte[] readFully(@NonNull InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buf, offset, length - offset);
            if (read < 0) {
                throw new EOFException();
            }
            offset += read;
        }
        return buf;
    }

    private static void writeString(@NonNull DataOutputStream out, @NonNull String s)
            throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            throw new IOException("String too long");
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    @NonNull
    private static String readString(@NonNull DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

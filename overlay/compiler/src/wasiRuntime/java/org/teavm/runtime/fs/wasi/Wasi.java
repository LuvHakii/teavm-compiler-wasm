/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.runtime.fs.wasi;

import java.nio.charset.StandardCharsets;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.runtime.heap.Heap;

/** WASI preview 1 file system calls. Pointers are plain ints into linear memory; see alloc/free. */
final class Wasi {
    static final int ERRNO_BADF = 8;
    static final int ERRNO_ISDIR = 31;
    static final int FILETYPE_DIRECTORY = 3;
    static final int FILETYPE_REGULAR_FILE = 4;
    static final int LOOKUP_FOLLOW_SYMLINKS = 1;
    static final int OFLAGS_CREATE = 1;
    static final int OFLAGS_DIRECTORY = 2;
    static final int OFLAGS_EXCLUSIVE = 4;
    static final int OFLAGS_TRUNCATE = 8;
    static final int FDFLAGS_APPEND = 1;
    static final int FSTFLAGS_MTIME = 4;
    static final long RIGHTS_READ = 1L << 1 | 1L << 2 | 1L << 5 | 1L << 21;
    static final long RIGHTS_WRITE = 1L << 4 | 1L << 6 | 1L << 22;
    static final long RIGHTS_READ_DIRECTORY = 1L << 14;

    private static final int MODULE_PRESTAT_DIR = 0;

    private Wasi() {
    }

    @Import(module = "wasi_snapshot_preview1", name = "fd_prestat_get")
    static native int fdPrestatGet(int fd, int prestat);

    @Import(module = "wasi_snapshot_preview1", name = "fd_prestat_dir_name")
    static native int fdPrestatDirName(int fd, int path, int length);

    @Import(module = "wasi_snapshot_preview1", name = "path_open")
    static native int pathOpen(int dirFd, int lookupFlags, int path, int pathLength, int openFlags, long rightsBase,
            long rightsInheriting, int fdFlags, int openedFd);

    @Import(module = "wasi_snapshot_preview1", name = "path_filestat_get")
    static native int pathFilestatGet(int dirFd, int lookupFlags, int path, int pathLength, int filestat);

    @Import(module = "wasi_snapshot_preview1", name = "path_filestat_set_times")
    static native int pathFilestatSetTimes(int dirFd, int lookupFlags, int path, int pathLength, long atime,
            long mtime, int fstFlags);

    @Import(module = "wasi_snapshot_preview1", name = "path_create_directory")
    static native int pathCreateDirectory(int dirFd, int path, int pathLength);

    @Import(module = "wasi_snapshot_preview1", name = "path_unlink_file")
    static native int pathUnlinkFile(int dirFd, int path, int pathLength);

    @Import(module = "wasi_snapshot_preview1", name = "path_remove_directory")
    static native int pathRemoveDirectory(int dirFd, int path, int pathLength);

    @Import(module = "wasi_snapshot_preview1", name = "path_rename")
    static native int pathRename(int oldDirFd, int oldPath, int oldPathLength, int newDirFd, int newPath,
            int newPathLength);

    @Import(module = "wasi_snapshot_preview1", name = "fd_readdir")
    static native int fdReaddir(int fd, int buffer, int bufferLength, long cookie, int used);

    @Import(module = "wasi_snapshot_preview1", name = "fd_filestat_get")
    static native int fdFilestatGet(int fd, int filestat);

    @Import(module = "wasi_snapshot_preview1", name = "fd_filestat_set_size")
    static native int fdFilestatSetSize(int fd, long size);

    @Import(module = "wasi_snapshot_preview1", name = "fd_read")
    static native int fdRead(int fd, int iovs, int count, int read);

    @Import(module = "wasi_snapshot_preview1", name = "fd_write")
    static native int fdWrite(int fd, int iovs, int count, int written);

    @Import(module = "wasi_snapshot_preview1", name = "fd_seek")
    static native int fdSeek(int fd, long offset, int whence, int newOffset);

    @Import(module = "wasi_snapshot_preview1", name = "fd_tell")
    static native int fdTell(int fd, int offset);

    @Import(module = "wasi_snapshot_preview1", name = "fd_close")
    static native int fdClose(int fd);

    @Import(module = "wasi_snapshot_preview1", name = "fd_sync")
    static native int fdSync(int fd);

    static int alloc(int bytes) {
        return Heap.alloc(bytes).toInt();
    }

    static void free(int pointer) {
        Heap.release(Address.fromInt(pointer));
    }

    static int getInt(int pointer) {
        return Address.fromInt(pointer).getInt();
    }

    static long getLong(int pointer) {
        return Address.fromInt(pointer).getLong();
    }

    static void putInt(int pointer, int value) {
        Address.fromInt(pointer).putInt(value);
    }

    static void copyIn(byte[] source, int offset, int length, int pointer) {
        var target = Address.fromInt(pointer);
        for (var i = 0; i < length; ++i) {
            target.add(i).putByte(source[offset + i]);
        }
    }

    static void copyOut(int pointer, byte[] target, int offset, int length) {
        var source = Address.fromInt(pointer);
        for (var i = 0; i < length; ++i) {
            target[offset + i] = source.add(i).getByte();
        }
    }

    /** Preopened directories as {fd, name} pairs, names without leading or trailing slash ("" is the root). */
    static Object[][] preopens() {
        var list = new java.util.ArrayList<Object[]>();
        var prestat = alloc(8);
        try {
            for (var fd = 3; fdPrestatGet(fd, prestat) == 0; ++fd) {
                if (Address.fromInt(prestat).getByte() != MODULE_PRESTAT_DIR) {
                    continue;
                }
                var length = getInt(prestat + 4);
                var name = alloc(Math.max(1, length));
                try {
                    if (fdPrestatDirName(fd, name, length) == 0) {
                        var bytes = new byte[length];
                        copyOut(name, bytes, 0, length);
                        list.add(new Object[] {fd, trimSlashes(new String(bytes, StandardCharsets.UTF_8))});
                    }
                } finally {
                    free(name);
                }
            }
        } finally {
            free(prestat);
        }
        return list.toArray(new Object[0][]);
    }

    static String trimSlashes(String path) {
        var start = 0;
        var end = path.length();
        while (start < end && path.charAt(start) == '/') {
            ++start;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            --end;
        }
        return path.substring(start, end);
    }
}

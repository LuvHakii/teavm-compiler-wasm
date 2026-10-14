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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;

/** A path below a preopened directory: dirFd is the preopen, path is relative to it ("." for the preopen itself). */
final class WasiFile implements VirtualFile {
    private static final int FILESTAT_SIZE = 64;
    private static final int DIRENT_SIZE = 24;
    private static final int DIRENT_BUFFER = 4096;

    private interface PathCall {
        int run(int path, int length);
    }

    private final int dirFd;
    private final String path;
    private final String name;

    WasiFile(int dirFd, String path, String name) {
        this.dirFd = dirFd;
        this.path = path;
        this.name = name;
    }

    private static int call(String path, PathCall call) {
        var bytes = path.getBytes(StandardCharsets.UTF_8);
        var pointer = Wasi.alloc(Math.max(1, bytes.length));
        try {
            Wasi.copyIn(bytes, 0, bytes.length, pointer);
            return call.run(pointer, bytes.length);
        } finally {
            Wasi.free(pointer);
        }
    }

    private String child(String childName) {
        return path.equals(".") ? childName : path + "/" + childName;
    }

    /** Opens a path below the preopen and returns the new fd, or -1. */
    private int open(String target, int openFlags, long rights, int fdFlags) {
        if (dirFd < 0) {
            return -1;
        }
        var result = Wasi.alloc(4);
        try {
            var errno = call(target, (p, n) -> Wasi.pathOpen(dirFd, Wasi.LOOKUP_FOLLOW_SYMLINKS, p, n, openFlags,
                    rights, rights, fdFlags, result));
            return errno == 0 ? Wasi.getInt(result) : -1;
        } finally {
            Wasi.free(result);
        }
    }

    /** Returns {filetype, size, modification time in ns}, or null when the path does not exist. */
    private long[] stat() {
        if (dirFd < 0) {
            return null;
        }
        var filestat = Wasi.alloc(FILESTAT_SIZE);
        try {
            var errno = call(path, (p, n) -> Wasi.pathFilestatGet(dirFd, Wasi.LOOKUP_FOLLOW_SYMLINKS, p, n, filestat));
            if (errno != 0) {
                return null;
            }
            return new long[] {
                org.teavm.interop.Address.fromInt(filestat + 16).getByte() & 0xFF,
                Wasi.getLong(filestat + 32),
                Wasi.getLong(filestat + 48)
            };
        } finally {
            Wasi.free(filestat);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isDirectory() {
        var stat = stat();
        return stat != null && stat[0] == Wasi.FILETYPE_DIRECTORY;
    }

    @Override
    public boolean isFile() {
        var stat = stat();
        return stat != null && stat[0] == Wasi.FILETYPE_REGULAR_FILE;
    }

    @Override
    public String[] listFiles() {
        var fd = open(path, Wasi.OFLAGS_DIRECTORY, Wasi.RIGHTS_READ | Wasi.RIGHTS_READ_DIRECTORY, 0);
        if (fd < 0) {
            return null;
        }
        var names = new ArrayList<String>();
        var buffer = Wasi.alloc(DIRENT_BUFFER + 4);
        try {
            var cookie = 0L;
            while (true) {
                if (Wasi.fdReaddir(fd, buffer, DIRENT_BUFFER, cookie, buffer + DIRENT_BUFFER) != 0) {
                    return null;
                }
                var used = Wasi.getInt(buffer + DIRENT_BUFFER);
                var position = 0;
                while (position + DIRENT_SIZE <= used) {
                    var length = Wasi.getInt(buffer + position + 16);
                    if (position + DIRENT_SIZE + length > used) {
                        break;
                    }
                    cookie = Wasi.getLong(buffer + position);
                    var bytes = new byte[length];
                    Wasi.copyOut(buffer + position + DIRENT_SIZE, bytes, 0, length);
                    var entry = new String(bytes, StandardCharsets.UTF_8);
                    if (!entry.equals(".") && !entry.equals("..")) {
                        names.add(entry);
                    }
                    position += DIRENT_SIZE + length;
                }
                if (used < DIRENT_BUFFER) {
                    return names.toArray(new String[0]);
                }
            }
        } finally {
            Wasi.free(buffer);
            Wasi.fdClose(fd);
        }
    }

    @Override
    public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
        try {
            return openChannel(path, readable, writable, append,
                    writable && !readable && !append ? Wasi.OFLAGS_TRUNCATE : 0);
        } catch (IOException e) {
            return null;
        }
    }

    VirtualFileAccessor openChannel(String displayPath, boolean readable, boolean writable, boolean append,
            int flags) throws IOException {
        if (dirFd < 0) {
            throw new java.nio.file.AccessDeniedException(displayPath);
        }
        long rights = 1L << 2 | 1L << 5 | 1L << 21 | 1L << 4;
        if (readable) {
            rights |= 1L << 1;
        }
        if (writable) {
            rights |= 1L << 6 | 1L << 22;
        }
        final long baseRights = rights;
        var result = Wasi.alloc(4);
        int fd;
        try {
            var errno = call(path, (p, n) -> Wasi.pathOpen(dirFd, Wasi.LOOKUP_FOLLOW_SYMLINKS, p, n, flags,
                    baseRights, 0, append ? Wasi.FDFLAGS_APPEND : 0, result));
            switch (errno) {
                case 0:
                    break;
                case 44:
                    throw new java.nio.file.NoSuchFileException(displayPath);
                case 20:
                    throw new java.nio.file.FileAlreadyExistsException(displayPath);
                case 2:
                case 63:
                case 69:
                case 76:
                    throw new java.nio.file.AccessDeniedException(displayPath);
                default:
                    throw new IOException(displayPath + ": path_open errno " + errno);
            }
            fd = Wasi.getInt(result);
        } finally {
            Wasi.free(result);
        }
        var accessor = new WasiAccessor(fd, append);
        try {
            var stat = Wasi.alloc(FILESTAT_SIZE);
            try {
                int errno = Wasi.fdFilestatGet(fd, stat);
                if (errno != 0) {
                    throw new IOException(displayPath + ": fd_filestat_get errno " + errno);
                }
                if ((org.teavm.interop.Address.fromInt(stat + 16).getByte() & 0xFF)
                        != Wasi.FILETYPE_REGULAR_FILE) {
                    throw new IOException(displayPath + ": not a regular file");
                }
            } finally {
                Wasi.free(stat);
            }
            accessor.seek(append ? accessor.size() : 0);
            return accessor;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                accessor.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    @Override
    public boolean createFile(String fileName) throws IOException {
        var fd = open(child(fileName), Wasi.OFLAGS_CREATE | Wasi.OFLAGS_EXCLUSIVE, Wasi.RIGHTS_WRITE, 0);
        if (fd < 0) {
            return false;
        }
        Wasi.fdClose(fd);
        return true;
    }

    @Override
    public boolean createDirectory(String fileName) {
        var target = child(fileName);
        // hosts disagree on whether creating an existing directory is an error
        return dirFd >= 0 && new WasiFile(dirFd, target, fileName).stat() == null
                && call(target, (p, n) -> Wasi.pathCreateDirectory(dirFd, p, n)) == 0;
    }

    @Override
    public boolean delete() {
        if (dirFd < 0 || path.equals(".")) {
            return false;
        }
        var errno = call(path, (p, n) -> Wasi.pathUnlinkFile(dirFd, p, n));
        if (errno == Wasi.ERRNO_ISDIR || errno != 0 && isDirectory()) {
            errno = call(path, (p, n) -> Wasi.pathRemoveDirectory(dirFd, p, n));
        }
        return errno == 0;
    }

    @Override
    public boolean adopt(VirtualFile file, String fileName) {
        var source = (WasiFile) file;
        if (dirFd < 0 || source.dirFd < 0 || fileName == null) {
            return false;
        }
        var target = child(fileName).getBytes(StandardCharsets.UTF_8);
        var targetPointer = Wasi.alloc(Math.max(1, target.length));
        try {
            Wasi.copyIn(target, 0, target.length, targetPointer);
            return call(source.path, (p, n) -> Wasi.pathRename(source.dirFd, p, n, dirFd, targetPointer,
                    target.length)) == 0;
        } finally {
            Wasi.free(targetPointer);
        }
    }

    @Override
    public boolean canRead() {
        return stat() != null;
    }

    @Override
    public boolean canWrite() {
        return stat() != null;
    }

    @Override
    public long lastModified() {
        var stat = stat();
        return stat == null ? 0 : stat[2] / 1_000_000L;
    }

    @Override
    public boolean setLastModified(long lastModified) {
        return dirFd >= 0 && call(path, (p, n) -> Wasi.pathFilestatSetTimes(dirFd, Wasi.LOOKUP_FOLLOW_SYMLINKS, p, n,
                0, lastModified * 1_000_000L, Wasi.FSTFLAGS_MTIME)) == 0;
    }

    @Override
    public boolean setReadOnly(boolean readOnly) {
        return false;
    }

    @Override
    public int length() {
        var stat = stat();
        return stat == null ? 0 : (int) stat[1];
    }
}

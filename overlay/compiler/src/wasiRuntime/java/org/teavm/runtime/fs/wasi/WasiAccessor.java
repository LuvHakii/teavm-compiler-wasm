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
import org.teavm.runtime.fs.VirtualFileAccessor;

final class WasiAccessor implements VirtualFileAccessor {
    private static final int IOVEC_SIZE = 8;
    private static final int RESULT_SIZE = 8;
    private static final int HEADER = IOVEC_SIZE + RESULT_SIZE;

    private final int fd;

    private final boolean append;

    WasiAccessor(int fd, boolean append) {
        this.fd = fd;
        this.append = append;
    }

    @Override
    public int read(byte[] buffer, int offset, int limit) throws IOException {
        if (limit == 0) {
            return 0;
        }
        var base = Wasi.alloc(HEADER + limit);
        try {
            Wasi.putInt(base, base + HEADER);
            Wasi.putInt(base + 4, limit);
            check(Wasi.fdRead(fd, base, 1, base + IOVEC_SIZE), "fd_read");
            var count = Wasi.getInt(base + IOVEC_SIZE);
            Wasi.copyOut(base + HEADER, buffer, offset, count);
            return count;
        } finally {
            Wasi.free(base);
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int limit) throws IOException {
        if (append) {
            seek(size());
        }
        var base = Wasi.alloc(HEADER + limit);
        try {
            Wasi.copyIn(buffer, offset, limit, base + HEADER);
            for (var done = 0; done < limit;) {
                Wasi.putInt(base, base + HEADER + done);
                Wasi.putInt(base + 4, limit - done);
                check(Wasi.fdWrite(fd, base, 1, base + IOVEC_SIZE), "fd_write");
                var count = Wasi.getInt(base + IOVEC_SIZE);
                if (count == 0) {
                    throw new IOException("fd_write wrote nothing");
                }
                done += count;
            }
        } finally {
            Wasi.free(base);
        }
    }

    @Override
    public int tell() throws IOException {
        var result = Wasi.alloc(RESULT_SIZE);
        try {
            check(Wasi.fdTell(fd, result), "fd_tell");
            return checkedSize(Wasi.getLong(result));
        } finally {
            Wasi.free(result);
        }
    }

    @Override
    public void seek(int target) throws IOException {
        seek(target, 0);
    }

    @Override
    public void skip(int amount) throws IOException {
        seek(amount, 1);
    }

    private void seek(long offset, int whence) throws IOException {
        var result = Wasi.alloc(RESULT_SIZE);
        try {
            check(Wasi.fdSeek(fd, offset, whence, result), "fd_seek");
        } finally {
            Wasi.free(result);
        }
    }

    @Override
    public int size() throws IOException {
        var filestat = Wasi.alloc(64);
        try {
            check(Wasi.fdFilestatGet(fd, filestat), "fd_filestat_get");
            return checkedSize(Wasi.getLong(filestat + 32));
        } finally {
            Wasi.free(filestat);
        }
    }

    @Override
    public void resize(int size) throws IOException {
        check(Wasi.fdFilestatSetSize(fd, size), "fd_filestat_set_size");
    }

    @Override
    public void close() throws IOException {
        check(Wasi.fdClose(fd), "fd_close");
    }

    @Override
    public void flush() throws IOException {
        check(Wasi.fdSync(fd), "fd_sync");
    }

    private static int checkedSize(long size) throws IOException {
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IOException("File offset exceeds signed-int range: " + size);
        }
        return (int) size;
    }

    private static void check(int errno, String call) throws IOException {
        if (errno != 0) {
            throw new IOException(call + " failed with errno " + errno);
        }
    }
}

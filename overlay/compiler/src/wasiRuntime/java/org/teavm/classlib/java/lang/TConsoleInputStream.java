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
package org.teavm.classlib.java.lang;

import java.io.IOException;
import org.teavm.classlib.java.io.TInputStream;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.runtime.heap.Heap;

class TConsoleInputStream extends TInputStream {
    private static final int IOVEC_SIZE = 8;
    private static final int RESULT_SIZE = 4;

    @Import(module = "wasi_snapshot_preview1", name = "fd_read")
    private static native int fdRead(int fd, Address iovs, int count, Address nread);

    @Override
    public int read() throws IOException {
        var one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        var base = Heap.alloc(IOVEC_SIZE + RESULT_SIZE + len);
        try {
            var data = base.add(IOVEC_SIZE + RESULT_SIZE);
            base.putAddress(data);
            base.add(4).putInt(len);
            var errno = fdRead(0, base, 1, base.add(IOVEC_SIZE));
            if (errno != 0) {
                throw new IOException("fd_read failed: " + errno);
            }
            var count = base.add(IOVEC_SIZE).getInt();
            if (count == 0) {
                return -1;
            }
            for (var i = 0; i < count; ++i) {
                b[off + i] = data.add(i).getByte();
            }
            return count;
        } finally {
            Heap.release(base);
        }
    }
}

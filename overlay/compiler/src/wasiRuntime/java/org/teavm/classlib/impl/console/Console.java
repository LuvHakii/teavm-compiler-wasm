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
package org.teavm.classlib.impl.console;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.runtime.heap.Heap;

/** Console output over WASI fd_write; replaces the teavmConsole putchar imports. */
public final class Console {
    private static final int IOVEC_SIZE = 8;
    private static final int RESULT_SIZE = 4;

    private Console() {
    }

    @Import(module = "wasi_snapshot_preview1", name = "fd_write")
    private static native int fdWrite(int fd, Address iovs, int count, Address written);

    public static void writeStdout(byte[] data, int off, int len) {
        write(1, data, off, len);
    }

    public static void writeStderr(byte[] data, int off, int len) {
        write(2, data, off, len);
    }

    static void write(int fd, byte[] data, int off, int len) {
        var base = Heap.alloc(IOVEC_SIZE + RESULT_SIZE + len);
        try {
            var bytes = base.add(IOVEC_SIZE + RESULT_SIZE);
            for (var i = 0; i < len; ++i) {
                bytes.add(i).putByte(data[off + i]);
            }
            var written = base.add(IOVEC_SIZE);
            for (var done = 0; done < len;) {
                base.putAddress(bytes.add(done));
                base.add(4).putInt(len - done);
                if (fdWrite(fd, base, 1, written) != 0 || written.getInt() == 0) {
                    return;
                }
                done += written.getInt();
            }
        } finally {
            Heap.release(base);
        }
    }
}

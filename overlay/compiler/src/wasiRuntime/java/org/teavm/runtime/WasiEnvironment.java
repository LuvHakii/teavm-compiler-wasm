package org.teavm.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.runtime.heap.Heap;

public final class WasiEnvironment {
    private static Map<String, String> environment;

    private WasiEnvironment() {
    }

    @Import(module = "wasi_snapshot_preview1", name = "environ_sizes_get")
    private static native int sizesGet(Address count, Address size);

    @Import(module = "wasi_snapshot_preview1", name = "environ_get")
    private static native int environGet(Address pointers, Address data);

    public static Map<String, String> get() {
        if (environment != null) {
            return environment;
        }
        var sizes = Heap.alloc(8);
        try {
            check(sizesGet(sizes, sizes.add(4)));
            int count = sizes.getInt();
            int size = sizes.add(4).getInt();
            if (count < 0 || size < 0 || count > (Integer.MAX_VALUE - size) / 4) {
                throw new IllegalStateException("Invalid WASI environment size");
            }
            var result = new HashMap<String, String>();
            if (count > 0) {
                var buffer = Heap.alloc(count * 4 + size);
                try {
                    var data = buffer.add(count * 4);
                    check(environGet(buffer, data));
                    for (int i = 0; i < count; ++i) {
                        var start = buffer.add(i * 4).getAddress();
                        int offset = start.toInt() - data.toInt();
                        if (offset < 0 || offset >= size) {
                            throw new IllegalStateException("Invalid WASI environment pointer");
                        }
                        int length = 0;
                        while (length < size - offset && start.add(length).getByte() != 0) {
                            ++length;
                        }
                        if (length == size - offset) {
                            throw new IllegalStateException("Unterminated WASI environment entry");
                        }
                        var bytes = new byte[length];
                        for (int j = 0; j < length; ++j) {
                            bytes[j] = start.add(j).getByte();
                        }
                        var entry = new String(bytes, StandardCharsets.UTF_8);
                        int separator = entry.indexOf('=');
                        if (separator < 1) {
                            throw new IllegalStateException("Invalid WASI environment entry");
                        }
                        result.put(entry.substring(0, separator), entry.substring(separator + 1));
                    }
                } finally {
                    Heap.release(buffer);
                }
            }
            environment = Collections.unmodifiableMap(result);
            return environment;
        } finally {
            Heap.release(sizes);
        }
    }

    private static void check(int errno) {
        if (errno != 0) {
            throw new IllegalStateException("WASI environment read failed: " + errno);
        }
    }
}

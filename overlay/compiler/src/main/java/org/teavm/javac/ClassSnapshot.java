/*
 *  Copyright 2025 konsoletyper.
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
package org.teavm.javac;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import org.teavm.cache.ClassIO;
import org.teavm.cache.SymbolTable;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.model.util.ModelUtils;

final class ClassSnapshot implements ClassHolderSource {
    private final Table symbols = new Table();
    private final Table files = new Table();
    private final Table variables = new Table();
    private final ClassIO io;
    private final ClassHolderSource parser;
    private final boolean record;
    private final long checksum;
    private final Map<String, byte[]> classes = new LinkedHashMap<>();
    private final Map<String, ClassHolder> loaded = new HashMap<>();

    ClassSnapshot(ReferenceCache referenceCache, ClassHolderSource parser, boolean record,
            Collection<FileData> classlib) {
        io = new ClassIO(referenceCache, symbols, files, variables);
        this.parser = parser;
        this.record = record;
        var crc = new CRC32();
        for (var file : classlib) {
            crc.update(file.path.getBytes(StandardCharsets.UTF_8));
            crc.update(file.data);
        }
        checksum = crc.getValue();
    }

    @Override
    public ClassHolder get(String name) {
        return loaded.computeIfAbsent(name, n -> {
            var bytes = classes.get(n);
            if (bytes != null) {
                return read(n, bytes);
            }
            var cls = parser.get(n);
            if (cls != null && record) {
                classes.put(n, write(cls));
            }
            return cls;
        });
    }

    private byte[] write(ClassHolder cls) {
        var output = new ByteArrayOutputStream();
        try {
            io.writeClass(output, cls);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.toByteArray();
    }

    private ClassHolder read(String name, byte[] bytes) {
        try {
            return ModelUtils.copyClass(io.readClass(new ByteArrayInputStream(bytes), name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    byte[] toBytes() {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeLong(checksum);
            for (var table : List.of(symbols, files, variables)) {
                output.writeInt(table.names.size());
                for (var name : table.names) {
                    output.writeUTF(name);
                }
            }
            output.writeInt(classes.size());
            for (var entry : classes.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeInt(entry.getValue().length);
                output.write(entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    void load(byte[] data) {
        try (var input = new DataInputStream(new ByteArrayInputStream(data))) {
            if (input.readLong() != checksum) {
                return;
            }
            for (var table : List.of(symbols, files, variables)) {
                var count = input.readInt();
                for (var i = 0; i < count; ++i) {
                    table.lookup(input.readUTF());
                }
            }
            var count = input.readInt();
            for (var i = 0; i < count; ++i) {
                var name = input.readUTF();
                var bytes = new byte[input.readInt()];
                input.readFully(bytes);
                classes.put(name, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class Table implements SymbolTable {
        final List<String> names = new ArrayList<>();
        private final Map<String, Integer> indexes = new HashMap<>();

        @Override
        public String at(int index) {
            return names.get(index);
        }

        @Override
        public int lookup(String symbol) {
            var index = indexes.get(symbol);
            if (index == null) {
                index = names.size();
                names.add(symbol);
                indexes.put(symbol, index);
            }
            return index;
        }
    }
}

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
import java.util.Objects;
import java.util.Set;
import org.teavm.classlib.java.nio.file.TOpenOption;
import java.nio.file.StandardOpenOption;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.classlib.java.nio.file.TPaths;
import org.teavm.runtime.WasiEnvironment;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileSystem;

public final class WasiFileSystem implements VirtualFileSystem {
    private Object[][] preopens;

    public VirtualFileAccessor openChannel(String path, Set<? extends TOpenOption> options) throws IOException {
        Objects.requireNonNull(options);
        for (Object option : options) {
            Objects.requireNonNull(option);
            if (option != StandardOpenOption.READ && option != StandardOpenOption.WRITE
                    && option != StandardOpenOption.APPEND && option != StandardOpenOption.CREATE
                    && option != StandardOpenOption.CREATE_NEW && option != StandardOpenOption.TRUNCATE_EXISTING) {
                throw new UnsupportedOperationException("Unsupported open option: " + option);
            }
        }
        boolean append = options.contains(StandardOpenOption.APPEND);
        boolean write = append || options.contains(StandardOpenOption.WRITE);
        boolean read = !write || options.contains(StandardOpenOption.READ);
        boolean truncate = options.contains(StandardOpenOption.TRUNCATE_EXISTING);
        if (append && (read || truncate)) {
            throw new IllegalArgumentException("APPEND conflicts with READ or TRUNCATE_EXISTING");
        }
        int flags = 0;
        if (write) {
            if (options.contains(StandardOpenOption.CREATE_NEW)) {
                flags = Wasi.OFLAGS_CREATE | Wasi.OFLAGS_EXCLUSIVE;
            } else {
                if (options.contains(StandardOpenOption.CREATE)) {
                    flags |= Wasi.OFLAGS_CREATE;
                }
                if (truncate) {
                    flags |= Wasi.OFLAGS_TRUNCATE;
                }
            }
        }
        return ((WasiFile) getFile(path)).openChannel(path, read, write, append, flags);
    }

    @Override
    public String getUserDir() {
        var pwd = WasiEnvironment.get().get("PWD");
        return pwd == null || !pwd.startsWith("/") ? "/" : TPaths.get(pwd).normalize().toString();
    }

    @Override
    public VirtualFile getFile(String path) {
        if (preopens == null) {
            preopens = Wasi.preopens();
        }
        var absolute = TPaths.get(path);
        if (!absolute.isAbsolute()) {
            absolute = TPaths.get(getUserDir()).resolve(absolute);
        }
        var relative = Wasi.trimSlashes(absolute.normalize().toString());
        var bestFd = -1;
        var bestName = "";
        for (var preopen : preopens) {
            var name = (String) preopen[1];
            var matches = name.isEmpty() || relative.equals(name) || relative.startsWith(name + "/");
            if (matches && (bestFd < 0 || name.length() > bestName.length())) {
                bestFd = (Integer) preopen[0];
                bestName = name;
            }
        }
        var inside = relative.length() > bestName.length() ? relative.substring(bestName.length()) : "";
        var local = Wasi.trimSlashes(inside);
        return new WasiFile(bestFd, local.isEmpty() ? "." : local, relative.substring(relative.lastIndexOf('/') + 1));
    }

    @Override
    public boolean isWindows() {
        return false;
    }

    @Override
    public String canonicalize(String path) {
        return path;
    }

    @Override
    public String[] getRoots() {
        return new String[] { "" };
    }
}

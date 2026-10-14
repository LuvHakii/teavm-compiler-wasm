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

import java.util.ArrayList;
import java.util.List;
import org.teavm.interop.DelegateTo;
import org.teavm.interop.Import;
import org.teavm.interop.Platforms;
import org.teavm.interop.SupportedOn;
import org.teavm.jso.browser.Navigator;
import org.teavm.runtime.GC;

// Replaces TeaVM's TRuntime: exit really terminates through WASI proc_exit and runs the shutdown hooks first.
public class TRuntime {
    private static final TRuntime instance = new TRuntime();
    private static Version version;

    private final List<Thread> hooks = new ArrayList<>();

    @Import(module = "wasi_snapshot_preview1", name = "proc_exit")
    private static native void procExit(int status);

    public void addShutdownHook(Thread hook) {
        if (hooks.contains(hook)) {
            throw new IllegalArgumentException("Hook previously registered");
        }
        hooks.add(hook);
    }

    public boolean removeShutdownHook(Thread hook) {
        return hooks.remove(hook);
    }

    public void exit(int status) {
        var pending = new ArrayList<>(hooks);
        hooks.clear();
        for (var hook : pending) {
            hook.start();
        }
        for (var hook : pending) {
            try {
                hook.join();
            } catch (InterruptedException e) {
                break;
            }
        }
        halt(status);
    }

    public void halt(int status) {
        procExit(status);
    }

    @DelegateTo("freeMemoryLowLevel")
    public long freeMemory() {
        return Integer.MAX_VALUE;
    }

    private long freeMemoryLowLevel() {
        return GC.getFreeMemory();
    }

    public void gc() {
        System.gc();
    }

    public static TRuntime getRuntime() {
        return instance;
    }

    @DelegateTo("totalMemoryLowLevel")
    public long totalMemory() {
        return Integer.MAX_VALUE;
    }

    private long totalMemoryLowLevel() {
        return GC.availableBytes();
    }

    public long maxMemory() {
        return Integer.MAX_VALUE;
    }

    @SupportedOn({Platforms.JAVASCRIPT, Platforms.WEBASSEMBLY_GC})
    public int availableProcessors() {
        return Navigator.hardwareConcurrency();
    }

    public static Version version() {
        if (version == null) {
            version = new Version();
        }
        return version;
    }

    public static final class Version implements Comparable<Version> {
        private Version() {
        }

        public int feature() {
            return 21;
        }

        @Override
        public int compareTo(Version o) {
            return 0;
        }
    }
}

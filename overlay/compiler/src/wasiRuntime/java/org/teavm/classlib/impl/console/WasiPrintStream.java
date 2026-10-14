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

/** Unbuffered PrintStream that writes UTF-8 bytes straight to a WASI file descriptor. */
abstract class WasiPrintStream extends JsConsolePrintStream {
    private final int fd;

    WasiPrintStream(int fd) {
        this.fd = fd;
    }

    @Override
    public void print(String s) {
        s = String.valueOf(s);
        var bytes = new byte[s.length() * 3];
        var n = 0;
        for (var i = 0; i < s.length(); ++i) {
            var c = (int) s.charAt(i);
            if (Character.isHighSurrogate((char) c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                c = Character.toCodePoint((char) c, s.charAt(++i));
            }
            if (c < 0x80) {
                bytes[n++] = (byte) c;
            } else if (c < 0x800) {
                bytes[n++] = (byte) (0xC0 | c >> 6);
                bytes[n++] = (byte) (0x80 | c & 0x3F);
            } else if (c < 0x10000) {
                bytes[n++] = (byte) (0xE0 | c >> 12);
                bytes[n++] = (byte) (0x80 | c >> 6 & 0x3F);
                bytes[n++] = (byte) (0x80 | c & 0x3F);
            } else {
                bytes[n++] = (byte) (0xF0 | c >> 18);
                bytes[n++] = (byte) (0x80 | c >> 12 & 0x3F);
                bytes[n++] = (byte) (0x80 | c >> 6 & 0x3F);
                bytes[n++] = (byte) (0x80 | c & 0x3F);
            }
        }
        write(bytes, 0, n);
    }

    @Override
    public void println(String s) {
        print(s + "\n");
    }

    @Override
    public void write(int b) {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        Console.write(fd, b, off, len);
    }

    @Override
    public void flush() {
    }
}

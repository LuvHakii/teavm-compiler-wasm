/*
 * Adapted from TypeThe0ry/Gaius f111ec1488a6bfd2d8e89ab9e9bcd72ea6b25245.
 * MIT License
 * Copyright (c) 2026 TypeTheory
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.teavm.classlib.java.nio.TByteBuffer;
import org.teavm.classlib.java.nio.TReadOnlyBufferException;
import org.teavm.classlib.java.nio.file.TOpenOption;
import org.teavm.classlib.java.nio.file.TPath;
import org.teavm.classlib.java.nio.file.TStandardOpenOption;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystemProvider;
import org.teavm.runtime.fs.wasi.WasiFileSystem;

public class TFileChannel implements TSeekableByteChannel {
    private final VirtualFileAccessor accessor;
    private final boolean readable;
    private final boolean writable;
    private final boolean append;
    private boolean open = true;
    private byte[] scratch;

    private TFileChannel(VirtualFileAccessor accessor, boolean readable, boolean writable, boolean append) {
        this.accessor = accessor;
        this.readable = readable;
        this.writable = writable;
        this.append = append;
    }

    public static TFileChannel open(TPath path, TOpenOption... options) throws IOException {
        return open(path, new HashSet<>(Arrays.asList(options)));
    }

    public static TFileChannel open(TPath path, Set<? extends TOpenOption> options,
            TFileAttribute<?>... attributes) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(options);
        Objects.requireNonNull(attributes);
        if (attributes.length != 0) {
            throw new UnsupportedOperationException("File creation attributes");
        }
        boolean append = options.contains(TStandardOpenOption.APPEND);
        boolean write = append || options.contains(TStandardOpenOption.WRITE);
        boolean read = !write || options.contains(TStandardOpenOption.READ);
        var accessor = ((WasiFileSystem) VirtualFileSystemProvider.getInstance()).openChannel(path.toString(), options);
        return new TFileChannel(accessor, read, write, append);
    }

    @Override
    public int read(TByteBuffer target) throws IOException {
        ensureReadable();
        if (target.isReadOnly()) {
            throw new TReadOnlyBufferException();
        }
        if (!target.hasRemaining()) {
            return 0;
        }
        int position = target.position();
        int count;
        if (target.hasArray()) {
            count = accessor.read(target.array(), target.arrayOffset() + position, target.remaining());
            if (count > 0) {
                target.position(position + count);
            }
        } else {
            var bytes = scratch();
            count = accessor.read(bytes, 0, Math.min(bytes.length, target.remaining()));
            if (count > 0) {
                target.put(bytes, 0, count);
            }
        }
        return count == 0 ? -1 : count;
    }

    public int read(TByteBuffer target, long position) throws IOException {
        ensureReadable();
        int index = toIndex(position);
        int old = accessor.tell();
        Throwable failure = null;
        try {
            accessor.seek(index);
            return read(target);
        } catch (IOException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            restore(old, failure);
        }
    }

    public long read(TByteBuffer[] targets, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, targets.length);
        ensureReadable();
        long total = 0;
        for (int i = offset; i < offset + length; ++i) {
            int count = read(targets[i]);
            if (count < 0) {
                return total == 0 ? -1 : total;
            }
            total += count;
            if (targets[i].hasRemaining()) {
                break;
            }
        }
        return total;
    }

    public long read(TByteBuffer[] targets) throws IOException {
        return read(targets, 0, targets.length);
    }

    @Override
    public int write(TByteBuffer source) throws IOException {
        ensureWritable();
        int count = source.remaining();
        if (count == 0) {
            return 0;
        }
        int start = append ? accessor.size() : accessor.tell();
        if ((long) start + count > Integer.MAX_VALUE) {
            throw new IOException("File write exceeds signed-int range");
        }
        int position = source.position();
        if (source.hasArray()) {
            accessor.write(source.array(), source.arrayOffset() + position, count);
        } else {
            var bytes = scratch();
            count = Math.min(count, bytes.length);
            for (int i = 0; i < count; ++i) {
                bytes[i] = source.get(position + i);
            }
            accessor.write(bytes, 0, count);
        }
        source.position(position + count);
        return count;
    }

    public int write(TByteBuffer source, long position) throws IOException {
        ensureWritable();
        int index = toIndex(position);
        int old = accessor.tell();
        Throwable failure = null;
        try {
            accessor.seek(index);
            return write(source);
        } catch (IOException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            restore(old, failure);
        }
    }

    private void restore(int position, Throwable failure) throws IOException {
        try {
            accessor.seek(position);
        } catch (IOException e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e);
        }
    }

    public long write(TByteBuffer[] sources, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, sources.length);
        ensureWritable();
        long total = 0;
        for (int i = offset; i < offset + length; ++i) {
            total += write(sources[i]);
            if (sources[i].hasRemaining()) {
                break;
            }
        }
        return total;
    }

    public long write(TByteBuffer[] sources) throws IOException {
        return write(sources, 0, sources.length);
    }

    @Override
    public long position() throws IOException {
        ensureOpen();
        return accessor.tell();
    }

    @Override
    public TFileChannel position(long position) throws IOException {
        ensureOpen();
        accessor.seek(toIndex(position));
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return accessor.size();
    }

    @Override
    public TFileChannel truncate(long size) throws IOException {
        ensureWritable();
        int target = toIndex(size);
        if (target < accessor.size()) {
            accessor.resize(target);
            if (accessor.tell() > target) {
                accessor.seek(target);
            }
        }
        return this;
    }

    public void force(boolean metadata) throws IOException {
        ensureOpen();
        accessor.flush();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            accessor.close();
        }
    }

    private byte[] scratch() {
        if (scratch == null) {
            scratch = new byte[8192];
        }
        return scratch;
    }

    private static int toIndex(long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("Negative file offset");
        }
        if (position > Integer.MAX_VALUE) {
            throw new IOException("File offset exceeds signed-int range: " + position);
        }
        return (int) position;
    }

    private void ensureOpen() throws TClosedChannelException {
        if (!open) {
            throw new TClosedChannelException();
        }
    }

    private void ensureReadable() throws IOException {
        ensureOpen();
        if (!readable) {
            throw new TNonReadableChannelException();
        }
    }

    private void ensureWritable() throws IOException {
        ensureOpen();
        if (!writable) {
            throw new TNonWritableChannelException();
        }
    }
}

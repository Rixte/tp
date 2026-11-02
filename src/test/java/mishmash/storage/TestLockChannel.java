package mishmash.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/** Delegates to a real channel while injecting lock acquisition and resource-release failures. */
final class TestLockChannel extends FileChannel {
    boolean returnNoLock;
    boolean failAcquisition;
    boolean failRelease;
    boolean failClose;
    boolean reportValidAfterClose;
    FileLock acquiredLock;
    final IOException acquisitionFailure = new IOException("Acquisition failed");
    RuntimeException acquisitionRuntimeFailure;
    private final FileChannel delegate;

    TestLockChannel(FileChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        if (returnNoLock) {
            return null;
        }
        if (failAcquisition) {
            throw acquisitionFailure;
        }
        if (acquisitionRuntimeFailure != null) {
            throw acquisitionRuntimeFailure;
        }
        FileLock acquired = delegate.tryLock(position, size, shared);
        acquiredLock = acquired;
        return new FileLock(this, position, size, shared) {
            @Override
            public boolean isValid() {
                return acquired.isValid() || reportValidAfterClose;
            }

            @Override
            public void release() throws IOException {
                acquired.release();
                if (failRelease) {
                    throw new IOException("Release failed");
                }
            }
        };
    }

    @Override
    protected void implCloseChannel() throws IOException {
        delegate.close();
        if (failClose) {
            throw new IOException("Close failed");
        }
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
        return delegate.read(destination);
    }

    @Override
    public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
        return delegate.read(destinations, offset, length);
    }

    @Override
    public int read(ByteBuffer destination, long position) throws IOException {
        return delegate.read(destination, position);
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
        return delegate.write(source);
    }

    @Override
    public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
        return delegate.write(sources, offset, length);
    }

    @Override
    public int write(ByteBuffer source, long position) throws IOException {
        return delegate.write(source, position);
    }

    @Override
    public long position() throws IOException {
        return delegate.position();
    }

    @Override
    public FileChannel position(long position) throws IOException {
        delegate.position(position);
        return this;
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        delegate.truncate(size);
        return this;
    }

    @Override
    public void force(boolean metadata) throws IOException {
        delegate.force(metadata);
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return delegate.transferTo(position, count, target);
    }

    @Override
    public long transferFrom(ReadableByteChannel source, long position, long count) throws IOException {
        return delegate.transferFrom(source, position, count);
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        return delegate.map(mode, position, size);
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return delegate.lock(position, size, shared);
    }
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Igor Konev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jephyr.integration.openjdk.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class NioSocketImpl extends SocketImpl {

    private boolean stream;
    private InetAddress localAddress;
    private NetworkChannel channel;
    private InputStream inputStream;
    private OutputStream outputStream;
    private int timeout;

    @Override
    protected void create(boolean stream) {
        this.stream = stream;
    }

    @Override
    protected void connect(String host, int port) throws IOException {
        connect(new InetSocketAddress(host, port), 0);
    }

    @Override
    protected void connect(InetAddress address, int port) throws IOException {
        connect(new InetSocketAddress(address, port), 0);
    }

    @Override
    protected void connect(SocketAddress address, int timeout) throws IOException {
        connect((InetSocketAddress) address, timeout);
    }

    private void connect(InetSocketAddress remoteAddress, int timeout) throws IOException {
        address = remoteAddress.getAddress();
        port = remoteAddress.getPort();
        if (stream) {
            connectStream(remoteAddress, timeout);
        } else {
            connectDatagram(remoteAddress);
        }
    }

    private void connectStream(InetSocketAddress remoteAddress, int timeout) throws IOException {
        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        try {
            if (localAddress != null) {
                channel.bind(new InetSocketAddress(localAddress, localport));
                localport = ((InetSocketAddress) channel.getLocalAddress()).getPort();
            }
            Future<Void> future = channel.connect(remoteAddress);
            try {
                if (timeout == 0) {
                    getUninterruptibly(future);
                } else {
                    try {
                        getUninterruptibly(future, timeout, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        throw new IOException(e);
                    }
                }
            } catch (ExecutionException e) {
                throw propagate(e.getCause(), IOException.class);
            }
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        this.channel = channel;
    }

    private void connectDatagram(InetSocketAddress remoteAddress) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        try {
            if (localAddress != null) {
                channel.bind(new InetSocketAddress(localAddress, localport));
                localport = ((InetSocketAddress) channel.getLocalAddress()).getPort();
            }
            channel.connect(remoteAddress);
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        this.channel = channel;
    }

    @Override
    protected void bind(InetAddress host, int port) {
        localAddress = host;
        localport = port;
    }

    @Override
    protected void listen(int backlog) throws IOException {
        AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open();
        try {
            if (localAddress != null) {
                channel.bind(new InetSocketAddress(localAddress, localport), backlog);
                localport = ((InetSocketAddress) channel.getLocalAddress()).getPort();
            }
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        this.channel = channel;
    }

    @Override
    protected void accept(SocketImpl s) throws IOException {
        NioSocketImpl impl = (NioSocketImpl) s;
        AsynchronousSocketChannel channel;
        Future<AsynchronousSocketChannel> future = ((AsynchronousServerSocketChannel) this.channel).accept();
        try {
            if (timeout == 0) {
                channel = getUninterruptibly(future);
            } else {
                try {
                    channel = getUninterruptibly(future, timeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    throw new IOException(e);
                }
            }
        } catch (ExecutionException e) {
            throw propagate(e.getCause(), IOException.class);
        }
        try {
            InetSocketAddress remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
            impl.address = remoteAddress.getAddress();
            impl.port = remoteAddress.getPort();
            impl.localport = ((InetSocketAddress) channel.getLocalAddress()).getPort();
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        impl.channel = channel;
    }

    @Override
    protected InputStream getInputStream() {
        if (inputStream == null) {
            if (channel instanceof AsynchronousByteChannel) {
                inputStream = new AsynchronousByteChannelInputStream((AsynchronousByteChannel) channel, timeout);
            } else {
                inputStream = Channels.newInputStream((ReadableByteChannel) channel);
            }
        }
        return inputStream;
    }

    @Override
    protected OutputStream getOutputStream() {
        if (outputStream == null) {
            if (channel instanceof AsynchronousByteChannel) {
                outputStream = new AsynchronousByteChannelOutputStream((AsynchronousByteChannel) channel);
            } else {
                outputStream = Channels.newOutputStream((WritableByteChannel) channel);
            }
        }
        return outputStream;
    }

    @Override
    protected int available() {
        return 0;
    }

    @Override
    protected void close() throws IOException {
        channel.close();
    }

    @Override
    protected void sendUrgentData(int data) throws IOException {
        if (outputStream == null) {
            if (channel instanceof AsynchronousByteChannel) {
                outputStream = new AsynchronousByteChannelOutputStream((AsynchronousByteChannel) channel);
            } else {
                outputStream = Channels.newOutputStream((WritableByteChannel) channel);
            }
        }
        outputStream.write(data);
        outputStream.flush();
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
        try {
            switch (optID) {
                case TCP_NODELAY:
                    channel.setOption(StandardSocketOptions.TCP_NODELAY, (Boolean) value);
                    return;
                case SO_REUSEADDR:
                    channel.setOption(StandardSocketOptions.SO_REUSEADDR, (Boolean) value);
                    return;
                case SO_BROADCAST:
                    channel.setOption(StandardSocketOptions.SO_BROADCAST, (Boolean) value);
                    return;
                case IP_MULTICAST_IF:
                    channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, (NetworkInterface) value);
                    return;
                case IP_MULTICAST_LOOP:
                    channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, (Boolean) value);
                    return;
                case IP_TOS:
                    channel.setOption(StandardSocketOptions.IP_TOS, (Integer) value);
                    return;
                case SO_LINGER:
                    if (channel.supportedOptions().contains(StandardSocketOptions.SO_LINGER)) {
                        if (value instanceof Boolean) {
                            channel.setOption(StandardSocketOptions.SO_LINGER, (Boolean) value ? 0 : -1);
                        } else {
                            channel.setOption(StandardSocketOptions.SO_LINGER, (Integer) value);
                        }
                    }
                    return;
                case SO_TIMEOUT:
                    timeout = (Integer) value;
                    return;
                case SO_SNDBUF:
                    channel.setOption(StandardSocketOptions.SO_SNDBUF, (Integer) value);
                    return;
                case SO_RCVBUF:
                    channel.setOption(StandardSocketOptions.SO_RCVBUF, (Integer) value);
                    return;
                case SO_KEEPALIVE:
                    channel.setOption(StandardSocketOptions.SO_KEEPALIVE, (Boolean) value);
                    return;
            }
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
        throw new SocketException("unrecognized TCP option: " + optID);
    }

    @Override
    public Object getOption(int optID) throws SocketException {
        SocketOption<?> option = findOption(optID);
        try {
            return channel.getOption(option);
        } catch (SocketException e) {
            throw e;
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    private static SocketOption<?> findOption(int optID) throws SocketException {
        switch (optID) {
            case TCP_NODELAY:
                return StandardSocketOptions.TCP_NODELAY;
            case SO_REUSEADDR:
                return StandardSocketOptions.SO_REUSEADDR;
            case SO_BROADCAST:
                return StandardSocketOptions.SO_BROADCAST;
            case IP_MULTICAST_IF:
                return StandardSocketOptions.IP_MULTICAST_IF;
            case IP_MULTICAST_LOOP:
                return StandardSocketOptions.IP_MULTICAST_LOOP;
            case IP_TOS:
                return StandardSocketOptions.IP_TOS;
            case SO_LINGER:
                return StandardSocketOptions.SO_LINGER;
            case SO_SNDBUF:
                return StandardSocketOptions.SO_SNDBUF;
            case SO_RCVBUF:
                return StandardSocketOptions.SO_RCVBUF;
            case SO_KEEPALIVE:
                return StandardSocketOptions.SO_KEEPALIVE;
            default:
                throw new SocketException("unrecognized TCP option: " + optID);
        }
    }

    static <V> V getUninterruptibly(Future<V> future) throws ExecutionException {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static <V> V getUninterruptibly(Future<V> future, long timeout, TimeUnit unit)
            throws ExecutionException, TimeoutException {
        boolean interrupted = false;
        try {
            long remainingNanos = unit.toNanos(timeout);
            long end = System.nanoTime() + remainingNanos;
            while (true) {
                try {
                    return future.get(remainingNanos, NANOSECONDS);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                    remainingNanos = end - System.nanoTime();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <E extends Throwable> RuntimeException propagate(Throwable exception, Class<E> cls) throws E {
        if (cls.isInstance(exception)) {
            throw cls.cast(exception);
        } else if (exception instanceof Error) {
            throw (Error) exception;
        } else if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        } else {
            throw new IllegalStateException(exception);
        }
    }

    private static final class AsynchronousByteChannelInputStream extends InputStream {

        private final AsynchronousByteChannel channel;
        private final int timeout;
        private ByteBuffer bb;
        private byte[] bs;
        private byte[] b1;

        AsynchronousByteChannelInputStream(AsynchronousByteChannel channel, int timeout) {
            this.channel = channel;
            this.timeout = timeout;
        }

        @Override
        public int read() throws IOException {
            if (b1 == null) {
                b1 = new byte[1];
            }
            int n = read(b1);
            if (n == 1) {
                return b1[0] & 0xff;
            }
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (off < 0 || off > b.length || len < 0 || off + len > b.length || off + len < 0) {
                throw new IndexOutOfBoundsException();
            }

            if (len == 0) {
                return 0;
            }

            ByteBuffer bb = bs == b ? this.bb : ByteBuffer.wrap(b);
            bb.position(off);
            bb.limit(Math.min(off + len, bb.capacity()));
            this.bb = bb;
            bs = b;

            Future<Integer> future = channel.read(bb);
            try {
                if (timeout == 0) {
                    return getUninterruptibly(future);
                } else {
                    try {
                        return getUninterruptibly(future, timeout, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        throw new IOException(e);
                    }
                }
            } catch (ExecutionException e) {
                throw propagate(e.getCause(), IOException.class);
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private static final class AsynchronousByteChannelOutputStream extends OutputStream {

        private final AsynchronousByteChannel channel;
        private ByteBuffer bb;
        private byte[] bs;
        private byte[] b1;

        AsynchronousByteChannelOutputStream(AsynchronousByteChannel channel) {
            this.channel = channel;
        }

        @Override
        public void write(int b) throws IOException {
            if (b1 == null) {
                b1 = new byte[1];
            }
            b1[0] = (byte) b;
            write(b1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (off < 0 || off > b.length || len < 0 || off + len > b.length || off + len < 0) {
                throw new IndexOutOfBoundsException();
            }

            if (len == 0) {
                return;
            }

            ByteBuffer bb = bs == b ? this.bb : ByteBuffer.wrap(b);
            bb.limit(Math.min(off + len, bb.capacity()));
            bb.position(off);
            this.bb = bb;
            bs = b;

            while (bb.hasRemaining()) {
                Future<?> future = channel.write(bb);
                try {
                    getUninterruptibly(future);
                } catch (ExecutionException e) {
                    throw propagate(e.getCause(), IOException.class);
                }
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}

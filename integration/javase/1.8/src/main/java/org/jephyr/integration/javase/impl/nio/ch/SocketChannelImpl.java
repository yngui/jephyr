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

package org.jephyr.integration.javase.impl.nio.ch;

import org.jephyr.integration.javase.java.nio.channels.SocketChannel;
import org.jephyr.integration.javase.java.nio.channels.spi.SelectorProvider;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Set;

final class SocketChannelImpl extends SocketChannel {

    private final AsynchronousSocketChannel channel;

    SocketChannelImpl(SelectorProvider provider, AsynchronousSocketChannel channel) {
        super(provider);
        this.channel = channel;
    }

    SocketChannelImpl(SelectorProvider provider) throws IOException {
        super(provider);
        channel = AsynchronousSocketChannel.open();
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        channel.bind(local);
        return this;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        channel.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return channel.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return channel.supportedOptions();
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        channel.shutdownInput();
        return this;
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        channel.shutdownOutput();
        return this;
    }

    @Override
    public Socket socket() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnected() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnectionPending() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        ResultHandler<Void> handler = new ResultHandler<>(channel);
        channel.connect(remote, null, handler);
        handler.result();
        return true;
    }

    @Override
    public boolean finishConnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return channel.getRemoteAddress();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ResultHandler<Integer> handler = new ResultHandler<>(channel);
        channel.read(dst, null, handler);
        return handler.result();
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        ResultHandler<Long> handler = new ResultHandler<>(channel);
        channel.read(dsts, offset, length, 0, null, null, handler);
        return handler.result();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ResultHandler<Integer> handler = new ResultHandler<>(channel);
        channel.write(src, null, handler);
        return handler.result();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        ResultHandler<Long> handler = new ResultHandler<>(channel);
        channel.write(srcs, offset, length, 0, null, null, handler);
        return handler.result();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        channel.close();
    }

    @Override
    protected void implConfigureBlocking(boolean block) {
        throw new UnsupportedOperationException();
    }
}

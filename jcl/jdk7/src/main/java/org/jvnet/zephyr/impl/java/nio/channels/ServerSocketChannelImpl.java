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

package org.jvnet.zephyr.impl.java.nio.channels;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

final class ServerSocketChannelImpl extends ServerSocketChannel {

    private final AsynchronousServerSocketChannel channel;

    ServerSocketChannelImpl(SelectorProvider selectorProvider) throws IOException {
        super(selectorProvider);
        channel = AsynchronousServerSocketChannel.open();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        channel.close();
    }

    @Override
    protected void implConfigureBlocking(boolean block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        channel.bind(local, backlog);
        return this;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
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
    public ServerSocket socket() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketChannel accept() throws IOException {
        ResultHandler<AsynchronousSocketChannel> handler = new ResultHandler<>();
        channel.accept(null, handler);
        return new SocketChannelImpl(provider(), handler.get());
    }
}

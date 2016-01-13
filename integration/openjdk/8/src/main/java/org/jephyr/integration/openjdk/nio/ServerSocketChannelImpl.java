package org.jephyr.integration.openjdk.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashSet;
import java.util.Set;

final class ServerSocketChannelImpl extends ServerSocketChannel {

    private final FakeServerSocket socket;

    ServerSocketChannelImpl(SelectorProvider provider) throws IOException {
        super(provider);
        socket = new FakeServerSocket();
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return new HashSet<>();
    }

    @Override
    public ServerSocket socket() {
        return socket;
    }

    @Override
    public SocketChannel accept() throws IOException {
        return new SocketChannelImpl(provider());
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        throw new UnsupportedOperationException();
    }
}

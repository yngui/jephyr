package org.jephyr.integration.openjdk.nio;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

import static java.util.Objects.requireNonNull;

public final class SelectorProviderImpl extends SelectorProvider {

    private final SelectorProvider provider;

    public SelectorProviderImpl(SelectorProvider provider) {
        this.provider = requireNonNull(provider);
    }

    @Override
    public DatagramChannel openDatagramChannel() throws IOException {
        return provider.openDatagramChannel();
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        return provider.openDatagramChannel(family);
    }

    @Override
    public Pipe openPipe() throws IOException {
        return provider.openPipe();
    }

    @Override
    public AbstractSelector openSelector() throws IOException {
        return new SelectorImpl(this);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        return new ServerSocketChannelImpl(this);
    }

    @Override
    public SocketChannel openSocketChannel() throws IOException {
        return new SocketChannelImpl(this);
    }
}

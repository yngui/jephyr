package org.jephyr.integration.openjdk.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashSet;
import java.util.Set;

final class SelectorImpl extends AbstractSelector {

    SelectorImpl(SelectorProvider provider) {
        super(provider);
    }

    @Override
    protected void implCloseSelector() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        return new SelectionKeyImpl();
    }

    @Override
    public Set<SelectionKey> keys() {
        return new HashSet<>();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int selectNow() throws IOException {
        return 0;
    }

    @Override
    public int select(long timeout) throws IOException {
        return 0;
    }

    @Override
    public int select() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Selector wakeup() {
        return this;
    }
}

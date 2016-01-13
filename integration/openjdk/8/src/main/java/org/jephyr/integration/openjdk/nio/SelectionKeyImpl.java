package org.jephyr.integration.openjdk.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

final class SelectionKeyImpl extends SelectionKey {

    @Override
    public SelectableChannel channel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Selector selector() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int interestOps() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SelectionKey interestOps(int ops) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int readyOps() {
        throw new UnsupportedOperationException();
    }
}

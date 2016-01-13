package org.jephyr.integration.openjdk.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

final class FakeServerSocket extends ServerSocket {

    FakeServerSocket() throws IOException {
    }

    @Override
    public void bind(SocketAddress endpoint) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
    }

    @Override
    public InetAddress getInetAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket accept() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerSocketChannel getChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBound() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
    }

    @Override
    public synchronized int getSoTimeout() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }
}

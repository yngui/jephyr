package org.jephyr.integration.openjdk.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

final class FakeSocket extends Socket {

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InetAddress getInetAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InetAddress getLocalAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketChannel getChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
    }

    @Override
    public int getSoLinger() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
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
    public void setKeepAlive(boolean on) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTrafficClass() throws SocketException {
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
    public synchronized void close() throws IOException {
    }

    @Override
    public void shutdownInput() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdownOutput() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnected() {
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
    public boolean isInputShutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOutputShutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }
}

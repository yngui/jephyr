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

package org.jephyr.integration.spring.boot.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.concurrent.Executor;

abstract class AbstractStatementImpl<T extends Statement> extends AbstractWrapperImpl<T> implements Statement {

    final Connection connection;

    AbstractStatementImpl(T delegate, Executor executor, Connection connection) {
        super(delegate, executor);
        this.connection = connection;
    }

    @Override
    public final ResultSet executeQuery(String sql) throws SQLException {
        return invoke(() -> delegate.executeQuery(sql), executor, SQLException.class);
    }

    @Override
    public final int executeUpdate(String sql) throws SQLException {
        return invoke(() -> delegate.executeUpdate(sql), executor, SQLException.class);
    }

    @Override
    public final void close() throws SQLException {
        invoke(() -> {
            delegate.close();
            return null;
        }, executor, SQLException.class);
    }

    @Override
    public final int getMaxFieldSize() throws SQLException {
        return delegate.getMaxFieldSize();
    }

    @Override
    public final void setMaxFieldSize(int max) throws SQLException {
        delegate.setMaxFieldSize(max);
    }

    @Override
    public final int getMaxRows() throws SQLException {
        return delegate.getMaxRows();
    }

    @Override
    public final void setMaxRows(int max) throws SQLException {
        delegate.setMaxRows(max);
    }

    @Override
    public final void setEscapeProcessing(boolean enable) throws SQLException {
        delegate.setEscapeProcessing(enable);
    }

    @Override
    public final int getQueryTimeout() throws SQLException {
        return delegate.getQueryTimeout();
    }

    @Override
    public final void setQueryTimeout(int seconds) throws SQLException {
        delegate.setQueryTimeout(seconds);
    }

    @Override
    public final void cancel() throws SQLException {
        delegate.cancel();
    }

    @Override
    public final SQLWarning getWarnings() throws SQLException {
        return delegate.getWarnings();
    }

    @Override
    public final void clearWarnings() throws SQLException {
        delegate.clearWarnings();
    }

    @Override
    public final void setCursorName(String name) throws SQLException {
        delegate.setCursorName(name);
    }

    @Override
    public final boolean execute(String sql) throws SQLException {
        return invoke(() -> delegate.execute(sql), executor, SQLException.class);
    }

    @Override
    public final ResultSet getResultSet() throws SQLException {
        return wrap(invoke(delegate::getResultSet, executor, SQLException.class), connection);
    }

    @Override
    public final int getUpdateCount() throws SQLException {
        return delegate.getUpdateCount();
    }

    @Override
    public final boolean getMoreResults() throws SQLException {
        return invoke(delegate::getMoreResults, executor, SQLException.class);
    }

    @Override
    public final void setFetchDirection(int direction) throws SQLException {
        delegate.setFetchDirection(direction);
    }

    @Override
    public final int getFetchDirection() throws SQLException {
        return delegate.getFetchDirection();
    }

    @Override
    public final void setFetchSize(int rows) throws SQLException {
        delegate.setFetchSize(rows);
    }

    @Override
    public final int getFetchSize() throws SQLException {
        return delegate.getFetchSize();
    }

    @Override
    public final int getResultSetConcurrency() throws SQLException {
        return delegate.getResultSetConcurrency();
    }

    @Override
    public final int getResultSetType() throws SQLException {
        return delegate.getResultSetType();
    }

    @Override
    public final void addBatch(String sql) throws SQLException {
        delegate.addBatch(sql);
    }

    @Override
    public final void clearBatch() throws SQLException {
        delegate.clearBatch();
    }

    @Override
    public final int[] executeBatch() throws SQLException {
        return invoke(delegate::executeBatch, executor, SQLException.class);
    }

    @Override
    public final Connection getConnection() {
        return connection;
    }

    @Override
    public final boolean getMoreResults(int current) throws SQLException {
        return invoke(() -> delegate.getMoreResults(current), executor, SQLException.class);
    }

    @Override
    public final ResultSet getGeneratedKeys() throws SQLException {
        return delegate.getGeneratedKeys();
    }

    @Override
    public final int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return invoke(() -> delegate.executeUpdate(sql, autoGeneratedKeys), executor, SQLException.class);
    }

    @Override
    public final int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return invoke(() -> delegate.executeUpdate(sql, columnIndexes), executor, SQLException.class);
    }

    @Override
    public final int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return invoke(() -> delegate.executeUpdate(sql, columnNames), executor, SQLException.class);
    }

    @Override
    public final boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return invoke(() -> delegate.execute(sql, autoGeneratedKeys), executor, SQLException.class);
    }

    @Override
    public final boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return invoke(() -> delegate.execute(sql, columnIndexes), executor, SQLException.class);
    }

    @Override
    public final boolean execute(String sql, String[] columnNames) throws SQLException {
        return invoke(() -> delegate.execute(sql, columnNames), executor, SQLException.class);
    }

    @Override
    public final int getResultSetHoldability() throws SQLException {
        return delegate.getResultSetHoldability();
    }

    @Override
    public final boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }

    @Override
    public final void setPoolable(boolean poolable) throws SQLException {
        delegate.setPoolable(poolable);
    }

    @Override
    public final boolean isPoolable() throws SQLException {
        return delegate.isPoolable();
    }

    @Override
    public final void closeOnCompletion() throws SQLException {
        delegate.closeOnCompletion();
    }

    @Override
    public final boolean isCloseOnCompletion() throws SQLException {
        return delegate.isCloseOnCompletion();
    }

    @Override
    public final long getLargeUpdateCount() throws SQLException {
        return delegate.getLargeUpdateCount();
    }

    @Override
    public final void setLargeMaxRows(long max) throws SQLException {
        delegate.setLargeMaxRows(max);
    }

    @Override
    public final long getLargeMaxRows() throws SQLException {
        return delegate.getLargeMaxRows();
    }

    @Override
    public final long[] executeLargeBatch() throws SQLException {
        return invoke(delegate::executeLargeBatch, executor, SQLException.class);
    }

    @Override
    public final long executeLargeUpdate(String sql) throws SQLException {
        return invoke(() -> delegate.executeLargeUpdate(sql), executor, SQLException.class);
    }

    @Override
    public final long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return invoke(() -> delegate.executeLargeUpdate(sql, autoGeneratedKeys), executor, SQLException.class);
    }

    @Override
    public final long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return invoke(() -> delegate.executeLargeUpdate(sql, columnIndexes), executor, SQLException.class);
    }

    @Override
    public final long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return invoke(() -> delegate.executeLargeUpdate(sql, columnNames), executor, SQLException.class);
    }
}

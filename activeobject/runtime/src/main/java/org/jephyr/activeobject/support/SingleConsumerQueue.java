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

package org.jephyr.activeobject.support;

import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public final class SingleConsumerQueue<E> extends AbstractQueue<E> implements Serializable {

    private static final long serialVersionUID = 6123082757231527184L;

    private final AtomicReference<Node<E>> head = new AtomicReference<>();
    private Node<E> tail;

    public SingleConsumerQueue() {
        Node<E> node = new Node<>();
        head.set(node);
        tail = node;
    }

    @Override
    public boolean add(E e) {
        return offer(e);
    }

    @Override
    public Iterator<E> iterator() {
        return new Itr<>(tail.next);
    }

    @Override
    public int size() {
        int size = 0;
        Node<E> next = tail.next;
        while (next != null) {
            size++;
            next = next.next;
        }
        return size;
    }

    @Override
    public boolean offer(E e) {
        requireNonNull(e);
        Node<E> node = new Node<>(e);
        Node<E> prev;
        do {
            prev = head.get();
        } while (!head.compareAndSet(prev, node));
        prev.next = node;
        return true;
    }

    @Override
    public E poll() {
        Node<E> next = tail.next;
        if (next == null) {
            return null;
        }
        E value = next.value;
        next.value = null;
        tail = next;
        return value;
    }

    @Override
    public E peek() {
        Node<E> next = tail.next;
        if (next == null) {
            return null;
        }
        return next.value;
    }

    private static final class Node<T> implements Serializable {

        private static final long serialVersionUID = -3507902457722206465L;

        T value;
        volatile Node<T> next;

        Node() {
        }

        Node(T value) {
            this.value = value;
        }
    }

    private static final class Itr<E> implements Iterator<E> {

        private Node<E> next;
        private E value;

        Itr(Node<E> next) {
            this.next = next;
        }

        @Override
        public boolean hasNext() {
            while (next != null) {
                value = next.value;
                if (value != null) {
                    return true;
                }
                next = next.next;
            }
            return false;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E value = this.value;
            this.value = null;
            next = next.next;
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

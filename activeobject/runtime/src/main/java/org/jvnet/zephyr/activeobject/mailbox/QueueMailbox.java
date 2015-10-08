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

package org.jvnet.zephyr.activeobject.mailbox;

import java.util.Queue;
import java.util.concurrent.locks.LockSupport;

import static java.util.Objects.requireNonNull;

public final class QueueMailbox implements Mailbox {

    private final Queue<Runnable> queue;
    private volatile Thread thread;

    public QueueMailbox(Queue<Runnable> queue) {
        this.queue = requireNonNull(queue);
    }

    @Override
    public void enqueue(Runnable task) {
        queue.add(task);
        LockSupport.unpark(thread);
    }

    @Override
    public Runnable dequeue() throws InterruptedException {
        thread = Thread.currentThread();
        Runnable message;
        while ((message = queue.poll()) == null) {
            LockSupport.park();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return message;
    }
}

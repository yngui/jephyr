package org.jephyr.continuation.easyflow;

import org.jephyr.continuation.UnsuspendableError;

public interface UnsuspendableErrorListener {

    void onUnsuspendableError(UnsuspendableError unsuspendableError);
}

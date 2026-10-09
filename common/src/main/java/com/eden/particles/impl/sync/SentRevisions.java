package com.eden.particles.impl.sync;

import java.util.Map;

/** Which revision of something each recipient last got, so repeated syncs only send what changed. */
public final class SentRevisions<P> {

    private final Map<P, Integer> sent;

    /** @param backing a weak map on the server, where player objects are new on every login */
    public SentRevisions(Map<P, Integer> backing) {
        this.sent = backing;
    }

    /** Records {@code revision} as sent to {@code recipient}; false if they already had it. */
    public boolean mark(P recipient, int revision) {
        Integer last = sent.put(recipient, revision);
        return last == null || last != revision;
    }
}

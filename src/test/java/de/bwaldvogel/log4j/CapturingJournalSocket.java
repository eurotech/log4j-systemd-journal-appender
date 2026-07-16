package de.bwaldvogel.log4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link JournalSocket} that captures the datagrams passed to
 * {@link #send(byte[])} instead of writing them to a real unix socket.
 */
class CapturingJournalSocket extends JournalSocket {

    private final List<byte[]> sentDatagrams = new ArrayList<>();

    CapturingJournalSocket() {
        super("/nonexistent/journal/socket");
    }

    @Override
    synchronized void send(byte[] data) {
        sentDatagrams.add(data);
    }

    List<byte[]> getSentDatagrams() {
        return sentDatagrams;
    }
}

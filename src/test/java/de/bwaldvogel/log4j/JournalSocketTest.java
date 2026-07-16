package de.bwaldvogel.log4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Test;

/**
 * Feature: deliver raw datagrams to the journal unix socket.
 */
public class JournalSocketTest {

    private FakeJournalDaemon daemon;
    private JournalSocket journalSocket;
    private Path plannedSocketPath;
    private Exception error;

    @After
    public void cleanup() throws IOException {
        if (journalSocket != null) {
            journalSocket.close();
        }
        if (daemon != null) {
            daemon.close();
        }
    }

    @Test
    public void sendToListeningJournalSocketDeliversTheDatagram() throws IOException {
        givenRunningJournalDaemon();
        givenJournalSocketForTheDaemon();

        whenDataIsSent("MESSAGE=hello\n");

        thenNoErrorOccurred();
        thenDaemonReceives("MESSAGE=hello\n");
    }

    @Test
    public void sendToMissingJournalSocketDropsTheDataWithoutError() {
        givenJournalSocketForMissingPath();

        whenDataIsSent("MESSAGE=hello\n");
        whenDataIsSent("MESSAGE=world\n");

        thenNoErrorOccurred();
    }

    @Test
    public void dataSentWhileTheSocketIsUnavailableIsDeliveredAfterReinitialization() throws IOException {
        givenJournalSocketPathWithNoListeningDaemon();
        givenJournalSocketForThatPath();
        givenAFailedSendAttempt();

        whenDataIsSent("MESSAGE=queued-1\n");
        whenDataIsSent("MESSAGE=queued-2\n");
        whenDaemonStartsListeningOnThatPath();
        whenSocketIsReinitialized();
        whenDataIsSent("MESSAGE=fresh\n");

        thenNoErrorOccurred();
        thenDaemonReceives("MESSAGE=queued-1\n");
        thenDaemonReceives("MESSAGE=queued-2\n");
        thenDaemonReceives("MESSAGE=fresh\n");
        thenDaemonReceivesNothingElse();
    }

    @Test
    public void closeCanBeCalledMultipleTimes() throws IOException {
        givenRunningJournalDaemon();
        givenJournalSocketForTheDaemon();

        whenSocketIsClosed();
        whenSocketIsClosed();

        thenNoErrorOccurred();
    }

    @Test
    public void sendAfterCloseQueuesTheDataWithoutError() throws IOException {
        givenRunningJournalDaemon();
        givenJournalSocketForTheDaemon();

        whenSocketIsClosed();
        whenDataIsSent("MESSAGE=while-closed\n");

        thenNoErrorOccurred();
        thenDaemonReceivesNothingElse();
    }

    private void givenRunningJournalDaemon() throws IOException {
        daemon = new FakeJournalDaemon();
    }

    private void givenJournalSocketForTheDaemon() {
        journalSocket = new JournalSocket(daemon.getSocketPath());
    }

    private void givenJournalSocketForMissingPath() {
        journalSocket = new JournalSocket("/nonexistent/journal/socket");
    }

    private void givenJournalSocketPathWithNoListeningDaemon() throws IOException {
        plannedSocketPath = FakeJournalDaemon.newTemporarySocketPath();
    }

    private void givenJournalSocketForThatPath() {
        journalSocket = new JournalSocket(plannedSocketPath.toString());
    }

    private void givenAFailedSendAttempt() {
        // the first failing send closes the socket; its payload is dropped
        journalSocket.send("MESSAGE=lost\n".getBytes(StandardCharsets.UTF_8));
    }

    private void whenDataIsSent(String data) {
        try {
            journalSocket.send(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            error = e;
        }
    }

    private void whenDaemonStartsListeningOnThatPath() throws IOException {
        daemon = new FakeJournalDaemon(plannedSocketPath);
    }

    private void whenSocketIsReinitialized() {
        try {
            journalSocket.init();
        } catch (IOException e) {
            error = e;
        }
    }

    private void whenSocketIsClosed() {
        try {
            journalSocket.close();
        } catch (Exception e) {
            error = e;
        }
    }

    private void thenNoErrorOccurred() {
        assertNull("Expected no error but got: " + error, error);
    }

    private void thenDaemonReceives(String expectedData) throws IOException {
        byte[] datagram = daemon.receiveDatagram(3000);
        assertEquals(expectedData, new String(datagram, StandardCharsets.UTF_8));
    }

    private void thenDaemonReceivesNothingElse() {
        assertTrue("Expected no further datagrams", daemon.receivesNothing());
    }
}

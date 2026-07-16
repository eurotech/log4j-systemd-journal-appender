package de.bwaldvogel.log4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Test;

/**
 * Feature: append log events through the journal socket.
 */
public class SystemdJournalAppenderTest {

    private CapturingJournalSocket journalSocket;
    private SystemdJournalAppender appender;
    private Exception appendError;

    @Test
    public void appendedEventIsEncodedAndSentThroughTheSocket() {
        givenJournalAppender();

        whenEventIsAppended(Level.INFO, "hello");

        thenNumberOfSentDatagramsIs(1);
        thenSentDatagramHasField(0, "MESSAGE", "hello");
        thenSentDatagramHasField(0, "PRIORITY", "6");
    }

    @Test
    public void appendedEventCarriesTheCurrentProcessPid() {
        givenJournalAppender();

        whenEventIsAppended(Level.INFO, "hello");

        thenSentDatagramHasField(0, "SYSLOG_PID", String.valueOf(ProcessHandle.current().pid()));
    }

    @Test
    public void appendedEventsAreSentInOrder() {
        givenJournalAppender();

        whenEventIsAppended(Level.INFO, "first");
        whenEventIsAppended(Level.WARN, "second");
        whenEventIsAppended(Level.ERROR, "third");

        thenNumberOfSentDatagramsIs(3);
        thenSentDatagramHasField(0, "MESSAGE", "first");
        thenSentDatagramHasField(1, "MESSAGE", "second");
        thenSentDatagramHasField(2, "MESSAGE", "third");
    }

    @Test
    public void appendedEventHonoursTheAppenderConfiguration() {
        givenJournalAppenderWithThreadNameLogging();

        whenEventIsAppended(Level.INFO, "hello");

        thenSentDatagramContainsField(0, "THREAD_NAME");
        thenSentDatagramHasField(0, "LOG4J_APPENDER", "TestJournal");
    }

    @Test
    public void appendingEventWithUnmappableLevelRaisesAnError() {
        givenJournalAppender();

        whenEventIsAppended(Level.OFF, "cannot be mapped");

        thenAppendFailedWith(IllegalArgumentException.class);
        thenNumberOfSentDatagramsIs(0);
    }

    private void givenJournalAppender() {
        journalSocket = new CapturingJournalSocket();
        AppenderConfiguration configuration = AppenderConfiguration.builder("TestJournal").build();
        appender = new SystemdJournalAppender(journalSocket, configuration);
    }

    private void givenJournalAppenderWithThreadNameLogging() {
        journalSocket = new CapturingJournalSocket();
        AppenderConfiguration configuration = AppenderConfiguration.builder("TestJournal") //
                .logThreadName(true) //
                .logAppenderName(true) //
                .build();
        appender = new SystemdJournalAppender(journalSocket, configuration);
    }

    private void whenEventIsAppended(Level level, String message) {
        Log4jLogEvent event = new Log4jLogEvent.Builder() //
                .setLevel(level) //
                .setMessage(new SimpleMessage(message)) //
                .setThreadName(Thread.currentThread().getName()) //
                .build();
        try {
            appender.append(event);
        } catch (Exception e) {
            appendError = e;
        }
    }

    private void thenNumberOfSentDatagramsIs(int expectedCount) {
        assertEquals(expectedCount, journalSocket.getSentDatagrams().size());
    }

    private void thenSentDatagramHasField(int datagramIndex, String key, String expectedValue) {
        thenAppendSucceeded();
        Map<String, String> fields = JournalProtocol.parse(journalSocket.getSentDatagrams().get(datagramIndex));
        assertEquals(expectedValue, fields.get(key));
    }

    private void thenSentDatagramContainsField(int datagramIndex, String key) {
        thenAppendSucceeded();
        Map<String, String> fields = JournalProtocol.parse(journalSocket.getSentDatagrams().get(datagramIndex));
        assertTrue("Expected field " + key + " to be present in " + fields.keySet(), fields.containsKey(key));
    }

    private void thenAppendSucceeded() {
        assertNull("Expected append to succeed but it failed with: " + appendError, appendError);
    }

    private void thenAppendFailedWith(Class<? extends Exception> expectedType) {
        assertNotNull("Expected append to fail but it succeeded", appendError);
        assertTrue("Expected error of type " + expectedType + " but was " + appendError.getClass(),
                expectedType.isInstance(appendError));
    }
}

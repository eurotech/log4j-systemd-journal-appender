package de.bwaldvogel.log4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Test;

/**
 * Feature: encode the message content and priority of a log event into the journal native protocol.
 */
public class EncodeMessageContentTest {

    private static final String PID = "1234";
    private static final int MAX_MESSAGE_SIZE = 60_000;
    private static final String TRUNCATION_MARKER = "[TRUNCATED]";

    private AppenderConfiguration.Builder configurationBuilder;
    private Log4jLogEvent.Builder eventBuilder;
    private byte[] encodedDatagram;
    private Map<String, String> fields;
    private Exception encodingError;

    @Test
    public void encodeSimpleMessage() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "hello journal");

        whenLogEventIsEncoded();

        thenFieldEquals("MESSAGE", "hello journal");
        thenFieldEquals("PRIORITY", "6");
        thenFieldEquals("SYSLOG_PID", PID);
        thenMessageIsTheFirstField();
    }

    @Test
    public void encodeFatalEventAsCriticalPriority() {
        givenMinimalConfiguration();
        givenLogEvent(Level.FATAL, "boom");

        whenLogEventIsEncoded();

        thenFieldEquals("PRIORITY", "2");
    }

    @Test
    public void encodeErrorEventAsErrorPriority() {
        givenMinimalConfiguration();
        givenLogEvent(Level.ERROR, "boom");

        whenLogEventIsEncoded();

        thenFieldEquals("PRIORITY", "3");
    }

    @Test
    public void encodeWarnEventAsWarningPriority() {
        givenMinimalConfiguration();
        givenLogEvent(Level.WARN, "careful");

        whenLogEventIsEncoded();

        thenFieldEquals("PRIORITY", "4");
    }

    @Test
    public void encodeInfoEventAsInfoPriority() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "fyi");

        whenLogEventIsEncoded();

        thenFieldEquals("PRIORITY", "6");
    }

    @Test
    public void encodeDebugEventAsDebugPriority() {
        givenMinimalConfiguration();
        givenLogEvent(Level.DEBUG, "details");

        whenLogEventIsEncoded();

        thenFieldEquals("PRIORITY", "7");
    }

    @Test
    public void encodeTraceEventAsDebugPriority() {
        givenMinimalConfiguration();
        givenLogEvent(Level.TRACE, "more details");

        whenLogEventIsEncoded();

        thenFieldEquals("PRIORITY", "7");
    }

    @Test
    public void encodeEventWithUnmappableLevelFails() {
        givenMinimalConfiguration();
        givenLogEvent(Level.OFF, "should not work");

        whenLogEventIsEncoded();

        thenEncodingFailedWith(IllegalArgumentException.class);
    }

    @Test
    public void encodeParameterizedMessage() {
        givenMinimalConfiguration();
        givenLogEventWithMessage(new ParameterizedMessage("Hello {}!", "World"));

        whenLogEventIsEncoded();

        thenFieldEquals("MESSAGE", "Hello World!");
    }

    @Test
    public void encodeMessageWithConfiguredLayout() {
        givenMinimalConfiguration();
        givenConfigurationWithLayoutPattern("%p: %m");
        givenLogEvent(Level.INFO, "hello");

        whenLogEventIsEncoded();

        thenFieldEquals("MESSAGE", "INFO: hello");
    }

    @Test
    public void encodeUnicodeMessage() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "unicode: →←üöß");

        whenLogEventIsEncoded();

        thenFieldEquals("MESSAGE", "unicode: →←üöß");
    }

    @Test
    public void encodeEmptyMessage() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "");

        whenLogEventIsEncoded();

        thenFieldEquals("MESSAGE", "");
    }

    @Test
    public void encodeMultilineMessageAsBinaryField() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "line one\nline two");

        whenLogEventIsEncoded();

        thenFieldEquals("MESSAGE", "line one\nline two");
        thenDatagramStartsWithBinaryMessageField();
    }

    @Test
    public void encodeMessageAtMaximumSizeIsKeptIntact() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "a".repeat(MAX_MESSAGE_SIZE));

        whenLogEventIsEncoded();

        thenMessageByteLengthIs(MAX_MESSAGE_SIZE);
        thenMessageDoesNotEndWithTruncationMarker();
    }

    @Test
    public void encodeOversizedMessageIsTruncatedWithMarker() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "a".repeat(MAX_MESSAGE_SIZE + 1));

        whenLogEventIsEncoded();

        thenMessageEndsWithTruncationMarker();
        thenMessageByteLengthIs(MAX_MESSAGE_SIZE);
    }

    @Test
    public void encodeOversizedMultibyteMessageIsTruncatedAtCharacterBoundary() {
        givenMinimalConfiguration();
        givenLogEvent(Level.INFO, "ü".repeat(MAX_MESSAGE_SIZE)); // 2 bytes per character

        whenLogEventIsEncoded();

        thenMessageEndsWithTruncationMarker();
        thenMessageByteLengthIsAtMost(MAX_MESSAGE_SIZE);
        thenMessageContainsNoBrokenCharacter();
    }

    private void givenMinimalConfiguration() {
        configurationBuilder = AppenderConfiguration.builder("TestAppender");
    }

    private void givenConfigurationWithLayoutPattern(String pattern) {
        configurationBuilder.layout(PatternLayout.newBuilder().withPattern(pattern).build());
    }

    private void givenLogEvent(Level level, String message) {
        eventBuilder = new Log4jLogEvent.Builder().setLevel(level).setMessage(new SimpleMessage(message));
    }

    private void givenLogEventWithMessage(org.apache.logging.log4j.message.Message message) {
        eventBuilder = new Log4jLogEvent.Builder().setLevel(Level.INFO).setMessage(message);
    }

    private void whenLogEventIsEncoded() {
        try {
            encodedDatagram = LogEventEncoder.encodeLogEvent(eventBuilder.build(), configurationBuilder.build(), PID);
            fields = JournalProtocol.parse(encodedDatagram);
        } catch (Exception e) {
            encodingError = e;
        }
    }

    private void thenFieldEquals(String key, String expectedValue) {
        thenEncodingSucceeded();
        assertEquals(expectedValue, fields.get(key));
    }

    private void thenMessageIsTheFirstField() {
        thenEncodingSucceeded();
        assertEquals("MESSAGE", JournalProtocol.firstKey(encodedDatagram));
    }

    private void thenDatagramStartsWithBinaryMessageField() {
        thenEncodingSucceeded();
        byte[] expectedHeader = "MESSAGE\n".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < expectedHeader.length; i++) {
            assertEquals("Byte " + i + " of the binary field header", expectedHeader[i], encodedDatagram[i]);
        }
    }

    private void thenMessageByteLengthIs(int expectedLength) {
        thenEncodingSucceeded();
        assertEquals(expectedLength, fields.get("MESSAGE").getBytes(StandardCharsets.UTF_8).length);
    }

    private void thenMessageByteLengthIsAtMost(int maxLength) {
        thenEncodingSucceeded();
        assertTrue(fields.get("MESSAGE").getBytes(StandardCharsets.UTF_8).length <= maxLength);
    }

    private void thenMessageEndsWithTruncationMarker() {
        thenEncodingSucceeded();
        assertTrue(fields.get("MESSAGE").endsWith(TRUNCATION_MARKER));
    }

    private void thenMessageDoesNotEndWithTruncationMarker() {
        thenEncodingSucceeded();
        assertFalse(fields.get("MESSAGE").endsWith(TRUNCATION_MARKER));
    }

    private void thenMessageContainsNoBrokenCharacter() {
        thenEncodingSucceeded();
        assertFalse("Message must not contain the unicode replacement character",
                fields.get("MESSAGE").contains("�"));
    }

    private void thenEncodingSucceeded() {
        assertNull("Expected encoding to succeed but it failed with: " + encodingError, encodingError);
        assertNotNull(fields);
    }

    private void thenEncodingFailedWith(Class<? extends Exception> expectedType) {
        assertNotNull("Expected encoding to fail but it succeeded", encodingError);
        assertTrue("Expected error of type " + expectedType + " but was " + encodingError.getClass(),
                expectedType.isInstance(encodingError));
    }
}

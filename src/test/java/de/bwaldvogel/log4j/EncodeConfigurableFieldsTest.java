package de.bwaldvogel.log4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.Test;

/**
 * Feature: encode the optional journal fields of a log event depending on the appender configuration.
 */
public class EncodeConfigurableFieldsTest {

    private static final String PID = "1234";

    private AppenderConfiguration.Builder configurationBuilder;
    private Log4jLogEvent.Builder eventBuilder;
    private Map<String, String> fields;
    private Exception encodingError;

    @Test
    public void encodeThreadNameWhenEnabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadName(true));
        givenLogEventWithThreadName("worker-1");

        whenLogEventIsEncoded();

        thenFieldEquals("THREAD_NAME", "worker-1");
    }

    @Test
    public void omitThreadNameWhenDisabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadName(false));
        givenLogEventWithThreadName("worker-1");

        whenLogEventIsEncoded();

        thenFieldIsAbsent("THREAD_NAME");
    }

    @Test
    public void encodeLoggerNameWhenEnabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logLoggerName(true));
        givenLogEventWithLoggerName("com.example.MyClass");

        whenLogEventIsEncoded();

        thenFieldEquals("LOG4J_LOGGER", "com.example.MyClass");
    }

    @Test
    public void omitLoggerNameWhenDisabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logLoggerName(false));
        givenLogEventWithLoggerName("com.example.MyClass");

        whenLogEventIsEncoded();

        thenFieldIsAbsent("LOG4J_LOGGER");
    }

    @Test
    public void encodeLoggerNameWithApplicationName() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logLoggerName(true).logLoggerAppName("MYAPP"));
        givenLogEventWithLoggerName("com.example.MyClass");

        whenLogEventIsEncoded();

        thenFieldEquals("MYAPP_LOGGER", "com.example.MyClass");
        thenFieldIsAbsent("LOG4J_LOGGER");
    }

    @Test
    public void emptyApplicationNameFallsBackToDefaultLoggerKey() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logLoggerName(true).logLoggerAppName(""));
        givenLogEventWithLoggerName("com.example.MyClass");

        whenLogEventIsEncoded();

        thenFieldEquals("LOG4J_LOGGER", "com.example.MyClass");
    }

    @Test
    public void encodeAppenderNameWhenEnabled() {
        givenConfigurationNamed("Journal");
        givenConfigurationValue(builder -> builder.logAppenderName(true));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldEquals("LOG4J_APPENDER", "Journal");
    }

    @Test
    public void omitAppenderNameWhenDisabled() {
        givenConfigurationNamed("Journal");
        givenConfigurationValue(builder -> builder.logAppenderName(false));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldIsAbsent("LOG4J_APPENDER");
    }

    @Test
    public void encodeStacktraceWhenEnabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logStacktrace(true));
        givenLogEventWithException(new RuntimeException("something failed"));

        whenLogEventIsEncoded();

        thenFieldContains("STACKTRACE", "java.lang.RuntimeException: something failed");
        thenFieldContains("STACKTRACE", "\tat ");
    }

    @Test
    public void omitStacktraceWhenDisabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logStacktrace(false));
        givenLogEventWithException(new RuntimeException("something failed"));

        whenLogEventIsEncoded();

        thenFieldIsAbsent("STACKTRACE");
    }

    @Test
    public void omitStacktraceWhenEventHasNoException() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logStacktrace(true));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldIsAbsent("STACKTRACE");
    }

    @Test
    public void encodeSourceLocationWhenEnabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logSource(true));
        givenLogEventWithSource(new StackTraceElement("com.example.MyClass", "myMethod", "MyClass.java", 42));

        whenLogEventIsEncoded();

        thenFieldEquals("CODE_FILE", "MyClass.java");
        thenFieldEquals("CODE_FUNC", "myMethod");
        thenFieldEquals("CODE_LINE", "42");
    }

    @Test
    public void omitSourceLocationWhenDisabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logSource(false));
        givenLogEventWithSource(new StackTraceElement("com.example.MyClass", "myMethod", "MyClass.java", 42));

        whenLogEventIsEncoded();

        thenFieldIsAbsent("CODE_FILE");
        thenFieldIsAbsent("CODE_FUNC");
        thenFieldIsAbsent("CODE_LINE");
    }

    @Test
    public void omitSourceLocationWhenUnavailable() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logSource(true));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldIsAbsent("CODE_FILE");
        thenFieldIsAbsent("CODE_FUNC");
        thenFieldIsAbsent("CODE_LINE");
    }

    @Test
    public void encodeThreadContextWithDefaultPrefix() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadContext(true));
        givenLogEventWithThreadContext("REQUESTID", "42");

        whenLogEventIsEncoded();

        thenFieldEquals("THREAD_CONTEXT_REQUESTID", "42");
    }

    @Test
    public void encodeThreadContextWithCustomPrefix() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadContext(true).threadContextPrefix("MYCTX_"));
        givenLogEventWithThreadContext("REQUESTID", "42");

        whenLogEventIsEncoded();

        thenFieldEquals("MYCTX_REQUESTID", "42");
    }

    @Test
    public void normalizeThreadContextPrefixAndKeys() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadContext(true).threadContextPrefix("my-ctx."));
        givenLogEventWithThreadContext("foo%s$1%d", "bar");

        whenLogEventIsEncoded();

        thenFieldEquals("MY_CTX_FOO_S_1_D", "bar");
    }

    @Test
    public void emptyThreadContextPrefixFallsBackToDefault() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadContext(true).threadContextPrefix(""));
        givenLogEventWithThreadContext("REQUESTID", "42");

        whenLogEventIsEncoded();

        thenFieldEquals("THREAD_CONTEXT_REQUESTID", "42");
    }

    @Test
    public void omitThreadContextWhenDisabled() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.logThreadContext(false));
        givenLogEventWithThreadContext("REQUESTID", "42");

        whenLogEventIsEncoded();

        thenFieldIsAbsent("THREAD_CONTEXT_REQUESTID");
    }

    @Test
    public void encodeSyslogIdentifierWhenConfigured() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.syslogIdentifier("my-service"));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldEquals("SYSLOG_IDENTIFIER", "my-service");
    }

    @Test
    public void omitSyslogIdentifierWhenNotConfigured() {
        givenConfiguration();
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldIsAbsent("SYSLOG_IDENTIFIER");
    }

    @Test
    public void omitSyslogIdentifierWhenEmpty() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.syslogIdentifier(""));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldIsAbsent("SYSLOG_IDENTIFIER");
    }

    @Test
    public void encodeSyslogFacilityWhenConfigured() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.syslogFacility(3));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldEquals("SYSLOG_FACILITY", "3");
    }

    @Test
    public void encodeLowestValidSyslogFacility() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.syslogFacility(0));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldEquals("SYSLOG_FACILITY", "0");
    }

    @Test
    public void encodeHighestValidSyslogFacility() {
        givenConfiguration();
        givenConfigurationValue(builder -> builder.syslogFacility(23));
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldEquals("SYSLOG_FACILITY", "23");
    }

    @Test
    public void omitSyslogFacilityByDefault() {
        givenConfiguration();
        givenLogEvent();

        whenLogEventIsEncoded();

        thenFieldIsAbsent("SYSLOG_FACILITY");
    }

    @Test
    public void configuringOutOfRangeSyslogFacilityFailsFast() {
        givenConfiguration();

        whenConfigurationValueIsApplied(builder -> builder.syslogFacility(24));

        thenConfigurationFailedWith(IllegalArgumentException.class);
    }

    @Test
    public void configuringNegativeSyslogFacilityOtherThanTheSentinelFailsFast() {
        givenConfiguration();

        whenConfigurationValueIsApplied(builder -> builder.syslogFacility(-2));

        thenConfigurationFailedWith(IllegalArgumentException.class);
    }

    private void givenConfiguration() {
        givenConfigurationNamed("TestAppender");
    }

    private void givenConfigurationNamed(String name) {
        configurationBuilder = AppenderConfiguration.builder(name);
        givenLogEvent();
    }

    private void givenConfigurationValue(Consumer<AppenderConfiguration.Builder> configurer) {
        configurer.accept(configurationBuilder);
    }

    private void givenLogEvent() {
        eventBuilder = new Log4jLogEvent.Builder().setLevel(Level.INFO).setMessage(new SimpleMessage("a message"));
    }

    private void givenLogEventWithThreadName(String threadName) {
        givenLogEvent();
        eventBuilder.setThreadName(threadName);
    }

    private void givenLogEventWithLoggerName(String loggerName) {
        givenLogEvent();
        eventBuilder.setLoggerName(loggerName);
    }

    private void givenLogEventWithException(Throwable throwable) {
        givenLogEvent();
        eventBuilder.setThrown(throwable);
    }

    private void givenLogEventWithSource(StackTraceElement source) {
        givenLogEvent();
        eventBuilder.setSource(source);
    }

    private void givenLogEventWithThreadContext(String key, String value) {
        givenLogEvent();
        StringMap contextData = new SortedArrayStringMap();
        contextData.putValue(key, value);
        eventBuilder.setContextData(contextData);
    }

    private void whenLogEventIsEncoded() {
        try {
            byte[] encoded = LogEventEncoder.encodeLogEvent(eventBuilder.build(), configurationBuilder.build(), PID);
            fields = JournalProtocol.parse(encoded);
        } catch (Exception e) {
            encodingError = e;
        }
    }

    private void whenConfigurationValueIsApplied(Consumer<AppenderConfiguration.Builder> configurer) {
        try {
            configurer.accept(configurationBuilder);
        } catch (Exception e) {
            encodingError = e;
        }
    }

    private void thenFieldEquals(String key, String expectedValue) {
        thenEncodingSucceeded();
        assertEquals(expectedValue, fields.get(key));
    }

    private void thenFieldContains(String key, String expectedPart) {
        thenEncodingSucceeded();
        assertNotNull("Expected field " + key + " to be present in " + fields.keySet(), fields.get(key));
        assertTrue("Expected field " + key + " to contain <" + expectedPart + "> but was <" + fields.get(key) + ">",
                fields.get(key).contains(expectedPart));
    }

    private void thenFieldIsAbsent(String key) {
        thenEncodingSucceeded();
        assertFalse("Expected field " + key + " to be absent but fields were " + fields.keySet(),
                fields.containsKey(key));
    }

    private void thenEncodingSucceeded() {
        if (encodingError != null) {
            throw new AssertionError("Expected encoding to succeed but it failed", encodingError);
        }
        assertNotNull(fields);
    }

    private void thenConfigurationFailedWith(Class<? extends Exception> expectedType) {
        assertNotNull("Expected configuring the builder to fail but it succeeded", encodingError);
        assertTrue("Expected error of type " + expectedType + " but was " + encodingError.getClass(),
                expectedType.isInstance(encodingError));
    }
}

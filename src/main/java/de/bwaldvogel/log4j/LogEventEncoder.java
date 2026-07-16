package de.bwaldvogel.log4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

public class LogEventEncoder {

    private static final int MAX_MESSAGE_SIZE = 60_000;
    private static final String TRUNCATED = "[TRUNCATED]";
    private static final byte[] TRUNCATED_MARKER = TRUNCATED.getBytes(StandardCharsets.UTF_8);

    private LogEventEncoder() {}

    public static byte[] encodeLogEvent(LogEvent event, AppenderConfiguration configuration, String pid) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        String message = buildFormattedMessage(event, configuration.getLayout());
        
        appendField(output, "MESSAGE", getTruncatedMessage(message));
        appendField(output, "PRIORITY", String.valueOf(log4jLevelToJournalPriority(event.getLevel())));
        appendField(output, "SYSLOG_PID", pid);

        appendThreadName(output, event, configuration);
        appendLoggerName(output, event, configuration);
        appendAppenderName(output, configuration);
        appendStacktrace(output, event, configuration);
        appendSource(output, event, configuration);
        appendThreadContext(output, event, configuration);
        appendSyslogIdentifier(output, configuration);
        appendSyslogFacility(output, configuration);

        ByteArrayOutputStream buf = new ByteArrayOutputStream(output.size());
        output.writeTo(buf);
        return buf.toByteArray();
    }

    private static void appendThreadName(ByteArrayOutputStream output, LogEvent event, AppenderConfiguration configuration) throws IOException {
        if (configuration.isLogThreadName()) {
            appendField(output, "THREAD_NAME", event.getThreadName());
        }
    }

    private static void appendLoggerName(ByteArrayOutputStream output, LogEvent event, AppenderConfiguration configuration) throws IOException {
        if (!configuration.isLogLoggerName()) {
            return;
        }
        Optional<String> logLoggerAppName = configuration.getLogLoggerAppName();
        String key = logLoggerAppName.map(loggerAppName -> loggerAppName + "_LOGGER").orElse("LOG4J_LOGGER");
        appendField(output, key, event.getLoggerName());
    }

    private static void appendAppenderName(ByteArrayOutputStream output, AppenderConfiguration configuration) throws IOException {
        if (configuration.isLogAppenderName()) {
            appendField(output, "LOG4J_APPENDER", configuration.getName());
        }
    }

    private static void appendStacktrace(ByteArrayOutputStream output, LogEvent event, AppenderConfiguration configuration) throws IOException {
        if (!configuration.isLogStacktrace() || event.getThrown() == null) {
            return;
        }
        StringWriter stacktrace = new StringWriter();
        event.getThrown().printStackTrace(new PrintWriter(stacktrace));
        appendField(output, "STACKTRACE", stacktrace.toString());
    }

    private static void appendSource(ByteArrayOutputStream output, LogEvent event, AppenderConfiguration configuration) throws IOException {
        if (!configuration.isLogSource() || event.getSource() == null) {
            return;
        }
        appendField(output, "CODE_FILE", event.getSource().getFileName());
        appendField(output, "CODE_FUNC", event.getSource().getMethodName());
        appendField(output, "CODE_LINE", String.valueOf(event.getSource().getLineNumber()));
    }

    private static void appendThreadContext(ByteArrayOutputStream output, LogEvent event, AppenderConfiguration configuration) throws IOException {
        if (!configuration.isLogThreadContext()) {
            return;
        }
        ReadOnlyStringMap context = event.getContextData();
        if (context == null) {
            return;
        }

        String threadContextPrefix = normalizeKey(configuration.getThreadContextPrefix());

        for (Entry<String, String> entry : context.toMap().entrySet()) {
            appendField(output, threadContextPrefix + normalizeKey(entry.getKey()), entry.getValue());
        }
    }

    private static void appendSyslogIdentifier(ByteArrayOutputStream output, AppenderConfiguration configuration) throws IOException {
        Optional<String> syslogIdentifier = configuration.getSyslogIdentifier();
        if (syslogIdentifier.isPresent()) {
            appendField(output, "SYSLOG_IDENTIFIER", syslogIdentifier.get());
        }
    }

    private static void appendSyslogFacility(ByteArrayOutputStream output, AppenderConfiguration configuration) throws IOException {
        if (configuration.getSyslogFacility() >= 0 && configuration.getSyslogFacility() < 24) {
            appendField(output, "SYSLOG_FACILITY", String.valueOf(configuration.getSyslogFacility()));
        }
    }

    /**
     *
     * @param out output stream where to append the new journal field. Written fields is key=value\n
     * @param key cannot be null
     * @param value can be null, in that case an empty value is written
     * @throws IOException if writing to input ByteArrayOutputStream fails. It is not possible
     */
    private static void appendField(ByteArrayOutputStream out, String key, String value) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        if (value == null) {
            out.write(keyBytes);
            out.write('=');
            out.write('\n');
            return;
        }
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);

        if (containsNewline(valBytes)) {
            out.write(keyBytes);
            out.write('\n');
            long len = valBytes.length;
            for (int i = 0; i < 8; i++) {
                out.write((int) (len & 0xFF));
                len >>= 8;
            }
            out.write(valBytes);
            out.write('\n');
        } else {
            out.write(keyBytes);
            out.write('=');
            out.write(valBytes);
            out.write('\n');
        }
    }

    private static boolean containsNewline(byte[] bytes) {
        for (byte b : bytes) {
            if (b == '\n') {
                return true;
            }
        }
        return false;
    }

    private static String buildFormattedMessage(LogEvent event, Layout<?> layout) {
        if (layout != null) {
            byte[] message = layout.toByteArray(event);
            return new String(message, StandardCharsets.UTF_8);
        }
        return event.getMessage().getFormattedMessage();
    }

    private static String getTruncatedMessage(String message) {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        if (msgBytes.length <= MAX_MESSAGE_SIZE) {
            return message;
        }

        int keepBytes = Math.max(0, MAX_MESSAGE_SIZE - TRUNCATED_MARKER.length);
        if (keepBytes == 0) {
            return TRUNCATED;
        }

        int cut = lastValidUtf8Boundary(msgBytes, keepBytes);
        return new String(msgBytes, 0, cut, StandardCharsets.UTF_8) + TRUNCATED;
    }

    // Walks backward from maxIndex to avoid cutting a multibyte UTF-8 character in half.
    private static int lastValidUtf8Boundary(byte[] bytes, int maxIndex) {
        int i = maxIndex;
        while (i > 0 && isUtf8ContinuationByte(bytes[i])) {
            i--;
        }
        return i;
    }

    // Continuation bytes in UTF-8 always start with the bit pattern 10xxxxxx.
    private static boolean isUtf8ContinuationByte(byte b) {
        return (b & 0xC0) == 0x80;
    }

    private static int log4jLevelToJournalPriority(Level level) {
        //
        // syslog.h
        //
        // #define LOG_EMERG 0 - system is unusable
        // #define LOG_ALERT 1 - action must be taken immediately
        // #define LOG_CRIT 2 - critical conditions
        // #define LOG_ERR 3 - error conditions
        // #define LOG_WARNING 4 - warning conditions
        // #define LOG_NOTICE 5 - normal but significant condition
        // #define LOG_INFO 6 - informational
        // #define LOG_DEBUG 7 - debug-level messages
        //
        return switch (level.getStandardLevel()) {
            case FATAL -> 2; // LOG_CRIT
            case ERROR -> 3; // LOG_ERR
            case WARN -> 4; // LOG_WARNING
            case INFO -> 6; // LOG_INFO
            case DEBUG, TRACE -> 7; // LOG_DEBUG
            default -> throw new IllegalArgumentException("Cannot map log level: " + level);
        };
    }

    private static String normalizeKey(String key) {
        return key.toUpperCase().replaceAll("[^_A-Z0-9]", "_");
    }
    
}

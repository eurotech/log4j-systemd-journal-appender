package de.bwaldvogel.log4j;

import java.util.Optional;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;

/**
 * Immutable configuration of a {@link SystemdJournalAppender}.
 * <p>
 * {@code name} is the only mandatory attribute and is passed to {@link #builder(String)}.
 * Every other attribute is optional: if left unset it falls back to the default documented on
 * the corresponding {@link Builder} method. Those defaults are exposed as the {@code DEFAULT_*}
 * constants below and are the same constants used by the {@code SystemdJournal} Log4j plugin
 * factory (see {@link SystemdJournalAppender#createAppender}), so a configuration built directly
 * in Java and one built from {@code log4j2.xml} always agree on what "unset" means.
 */
public final class AppenderConfiguration {

    public static final boolean DEFAULT_IGNORE_EXCEPTIONS = true;
    public static final boolean DEFAULT_LOG_SOURCE = false;
    public static final boolean DEFAULT_LOG_STACKTRACE = true;
    public static final boolean DEFAULT_LOG_THREAD_NAME = true;
    public static final boolean DEFAULT_LOG_LOGGER_NAME = true;
    public static final boolean DEFAULT_LOG_APPENDER_NAME = true;
    public static final boolean DEFAULT_LOG_THREAD_CONTEXT = true;
    public static final String DEFAULT_THREAD_CONTEXT_PREFIX = "THREAD_CONTEXT_";

    /** Value of {@link #getSyslogFacility()} meaning "emit no {@code SYSLOG_FACILITY} field". This is the default. */
    public static final int NO_SYSLOG_FACILITY = -1;
    public static final int MIN_SYSLOG_FACILITY = 0;
    public static final int MAX_SYSLOG_FACILITY = 23;

    private final String name;
    private final Filter filter;
    private final Layout<?> layout;
    private final boolean ignoreExceptions;
    private final boolean logStacktrace;
    private final boolean logSource;
    private final boolean logThreadName;
    private final boolean logLoggerName;
    private final boolean logAppenderName;
    private final String logLoggerAppName;
    private final boolean logThreadContext;
    private final String threadContextPrefix;
    private final String syslogIdentifier;
    private final int syslogFacility;

    private AppenderConfiguration(Builder builder) {
        this.name = builder.name;
        this.filter = builder.filter;
        this.layout = builder.layout;
        this.ignoreExceptions = builder.ignoreExceptions;
        this.logStacktrace = builder.logStacktrace;
        this.logSource = builder.logSource;
        this.logThreadName = builder.logThreadName;
        this.logLoggerName = builder.logLoggerName;
        this.logAppenderName = builder.logAppenderName;
        this.logLoggerAppName = builder.logLoggerAppName;
        this.logThreadContext = builder.logThreadContext;
        this.threadContextPrefix = builder.threadContextPrefix;
        this.syslogIdentifier = builder.syslogIdentifier;
        this.syslogFacility = builder.syslogFacility;
    }

    /**
     * Starts building a configuration.
     *
     * @param name the appender's name. Mandatory: every other attribute is optional.
     * @throws IllegalArgumentException if {@code name} is {@code null} or empty
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    /** No default; {@code null} means no filtering is applied by this appender. */
    public Filter getFilter() {
        return filter;
    }

    /** No default; {@code null} means the event's raw formatted message is used as-is. */
    public Layout<?> getLayout() {
        return layout;
    }

    public boolean isIgnoreExceptions() {
        return ignoreExceptions;
    }

    public boolean isLogStacktrace() {
        return logStacktrace;
    }

    public boolean isLogSource() {
        return logSource;
    }

    public boolean isLogThreadName() {
        return logThreadName;
    }

    public boolean isLogLoggerName() {
        return logLoggerName;
    }

    public boolean isLogAppenderName() {
        return logAppenderName;
    }

    /** No default; absent means the plain {@code LOG4J_LOGGER} key is used. */
    public Optional<String> getLogLoggerAppName() {
        return nonBlank(logLoggerAppName);
    }

    public boolean isLogThreadContext() {
        return logThreadContext;
    }

    /** Always present; defaults to {@value #DEFAULT_THREAD_CONTEXT_PREFIX}. */
    public String getThreadContextPrefix() {
        return threadContextPrefix;
    }

    /** No default; absent means no {@code SYSLOG_IDENTIFIER} field is emitted. */
    public Optional<String> getSyslogIdentifier() {
        return nonBlank(syslogIdentifier);
    }

    /** @return the configured facility in [{@value #MIN_SYSLOG_FACILITY}, {@value #MAX_SYSLOG_FACILITY}], or {@value #NO_SYSLOG_FACILITY} (the default) if none is configured. */
    public int getSyslogFacility() {
        return syslogFacility;
    }

    private static Optional<String> nonBlank(String value) {
        return value != null && !value.isEmpty() ? Optional.of(value) : Optional.empty();
    }

    /**
     * Builder for {@link AppenderConfiguration}. Every setter's Javadoc states whether the
     * attribute is mandatory or, if optional, which default value applies when it is left unset.
     */
    public static final class Builder {

        private final String name;
        private Filter filter;
        private Layout<?> layout;
        private boolean ignoreExceptions = DEFAULT_IGNORE_EXCEPTIONS;
        private boolean logStacktrace = DEFAULT_LOG_STACKTRACE;
        private boolean logSource = DEFAULT_LOG_SOURCE;
        private boolean logThreadName = DEFAULT_LOG_THREAD_NAME;
        private boolean logLoggerName = DEFAULT_LOG_LOGGER_NAME;
        private boolean logAppenderName = DEFAULT_LOG_APPENDER_NAME;
        private String logLoggerAppName;
        private boolean logThreadContext = DEFAULT_LOG_THREAD_CONTEXT;
        private String threadContextPrefix = DEFAULT_THREAD_CONTEXT_PREFIX;
        private String syslogIdentifier;
        private int syslogFacility = NO_SYSLOG_FACILITY;

        private Builder(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is mandatory and must not be null or empty");
            }
            this.name = name;
        }

        /** Optional. No default ({@code null} means no filtering). */
        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        /** Optional. No default ({@code null} means the raw formatted message is used, without rendering a layout). */
        public Builder layout(Layout<?> layout) {
            this.layout = layout;
            return this;
        }

        /** Optional. Default: {@code true}. */
        public Builder ignoreExceptions(boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        /** Optional. Default: {@code true}. Logs the exception stacktrace in the {@code STACKTRACE} field. */
        public Builder logStacktrace(boolean logStacktrace) {
            this.logStacktrace = logStacktrace;
            return this;
        }

        /** Optional. Default: {@code false}. Capturing the call site ({@code CODE_FILE}/{@code CODE_FUNC}/{@code CODE_LINE}) is expensive. */
        public Builder logSource(boolean logSource) {
            this.logSource = logSource;
            return this;
        }

        /** Optional. Default: {@code true}. */
        public Builder logThreadName(boolean logThreadName) {
            this.logThreadName = logThreadName;
            return this;
        }

        /** Optional. Default: {@code true}. */
        public Builder logLoggerName(boolean logLoggerName) {
            this.logLoggerName = logLoggerName;
            return this;
        }

        /** Optional. Default: {@code true}. */
        public Builder logAppenderName(boolean logAppenderName) {
            this.logAppenderName = logAppenderName;
            return this;
        }

        /** Optional. No default (absent means the logger name is emitted under the plain {@code LOG4J_LOGGER} key). */
        public Builder logLoggerAppName(String logLoggerAppName) {
            this.logLoggerAppName = logLoggerAppName;
            return this;
        }

        /** Optional. Default: {@code true}. */
        public Builder logThreadContext(boolean logThreadContext) {
            this.logThreadContext = logThreadContext;
            return this;
        }

        /** Optional. Default: {@value #DEFAULT_THREAD_CONTEXT_PREFIX}. A {@code null} or empty value resets it to the default. */
        public Builder threadContextPrefix(String threadContextPrefix) {
            this.threadContextPrefix = threadContextPrefix == null || threadContextPrefix.isEmpty() //
                    ? DEFAULT_THREAD_CONTEXT_PREFIX //
                    : threadContextPrefix;
            return this;
        }

        /** Optional. No default (absent means no {@code SYSLOG_IDENTIFIER} field is emitted). */
        public Builder syslogIdentifier(String syslogIdentifier) {
            this.syslogIdentifier = syslogIdentifier;
            return this;
        }

        /**
         * Optional. Default: {@value #NO_SYSLOG_FACILITY} (no {@code SYSLOG_FACILITY} field is emitted).
         *
         * @throws IllegalArgumentException if {@code syslogFacility} is neither {@value #NO_SYSLOG_FACILITY}
         *         nor within [{@value #MIN_SYSLOG_FACILITY}, {@value #MAX_SYSLOG_FACILITY}]
         */
        public Builder syslogFacility(int syslogFacility) {
            boolean disabled = syslogFacility == NO_SYSLOG_FACILITY;
            boolean inRange = syslogFacility >= MIN_SYSLOG_FACILITY && syslogFacility <= MAX_SYSLOG_FACILITY;
            if (!disabled && !inRange) {
                throw new IllegalArgumentException("syslogFacility must be " + NO_SYSLOG_FACILITY + " (disabled) or between "
                        + MIN_SYSLOG_FACILITY + " and " + MAX_SYSLOG_FACILITY + ", but was: " + syslogFacility);
            }
            this.syslogFacility = syslogFacility;
            return this;
        }

        /**
         * @return an immutable {@link AppenderConfiguration} with the attributes configured so far
         */
        public AppenderConfiguration build() {
            return new AppenderConfiguration(this);
        }
    }
}

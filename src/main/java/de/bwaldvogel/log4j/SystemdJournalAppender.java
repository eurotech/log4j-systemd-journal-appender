package de.bwaldvogel.log4j;

import java.io.IOException;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.Booleans;
import org.apache.logging.log4j.core.util.Integers;

@Plugin(name = "SystemdJournal", category = "Core", elementType = "appender", printObject = true)
public class SystemdJournalAppender extends AbstractAppender {

    private final JournalSocket journalSocket;
    private final AppenderConfiguration configuration;
    private String pid = getCurrentProcessPid();

    SystemdJournalAppender(JournalSocket journalSocket, AppenderConfiguration configuration) {
        super(configuration.getName(), configuration.getFilter(), configuration.getLayout(), configuration.isIgnoreExceptions());
        this.journalSocket = journalSocket;
        this.configuration = configuration;
    }

    /**
     * Log4j plugin factory. {@code name} is the only mandatory attribute; every other attribute
     * is optional and falls back to the default documented on the matching
     * {@link AppenderConfiguration.Builder} method. Any invalid attribute value (e.g. a
     * {@code syslogFacility} outside of its valid range, or one that isn't a number) is reported
     * with {@link #LOGGER} and results in no appender being created, rather than throwing out of
     * the Log4j configuration bootstrap.
     */
    @PluginFactory
    public static SystemdJournalAppender createAppender(@PluginAttribute("name") final String name,
            @PluginAttribute("ignoreExceptions") final String ignoreExceptionsString,
            @PluginAttribute("logSource") final String logSourceString,
            @PluginAttribute("logStacktrace") final String logStacktraceString,
            @PluginAttribute("logLoggerName") final String logLoggerNameString,
            @PluginAttribute("logAppenderName") final String logAppenderNameString,
            @PluginAttribute("logLoggerAppName") final String logLoggerAppName,
            @PluginAttribute("logThreadName") final String logThreadNameString,
            @PluginAttribute("logThreadContext") final String logThreadContextString,
            @PluginAttribute("threadContextPrefix") final String threadContextPrefix,
            @PluginAttribute("syslogIdentifier") final String syslogIdentifier,
            @PluginAttribute("syslogFacility") final String syslogFacilityString,
            @PluginElement("Layout") final Layout<?> layout,
            @PluginElement("Filter") final Filter filter,
            @PluginConfiguration final Configuration config) {

        if (name == null || name.isEmpty()) {
            LOGGER.error("No name provided for SystemdJournalAppender");
            return null;
        }

        final AppenderConfiguration configuration;
        try {
            final int syslogFacility = Integers.parseInt(syslogFacilityString, AppenderConfiguration.NO_SYSLOG_FACILITY);
            configuration = AppenderConfiguration.builder(name) //
                    .filter(filter) //
                    .layout(layout) //
                    .ignoreExceptions(Booleans.parseBoolean(ignoreExceptionsString, AppenderConfiguration.DEFAULT_IGNORE_EXCEPTIONS)) //
                    .logSource(Booleans.parseBoolean(logSourceString, AppenderConfiguration.DEFAULT_LOG_SOURCE)) //
                    .logStacktrace(Booleans.parseBoolean(logStacktraceString, AppenderConfiguration.DEFAULT_LOG_STACKTRACE)) //
                    .logThreadName(Booleans.parseBoolean(logThreadNameString, AppenderConfiguration.DEFAULT_LOG_THREAD_NAME)) //
                    .logLoggerName(Booleans.parseBoolean(logLoggerNameString, AppenderConfiguration.DEFAULT_LOG_LOGGER_NAME)) //
                    .logAppenderName(Booleans.parseBoolean(logAppenderNameString, AppenderConfiguration.DEFAULT_LOG_APPENDER_NAME)) //
                    .logLoggerAppName(logLoggerAppName) //
                    .logThreadContext(Booleans.parseBoolean(logThreadContextString, AppenderConfiguration.DEFAULT_LOG_THREAD_CONTEXT)) //
                    .threadContextPrefix(threadContextPrefix) //
                    .syslogIdentifier(syslogIdentifier) //
                    .syslogFacility(syslogFacility) //
                    .build();
        } catch (IllegalArgumentException e) {
            // covers both NumberFormatException (syslogFacility isn't a number) and the builder's
            // own validation (e.g. syslogFacility out of range)
            LOGGER.error("Invalid configuration for SystemdJournalAppender '{}': {}", name, e.getMessage());
            return null;
        }

        SystemdJournalAppender appender = new SystemdJournalAppender(new JournalSocket(), configuration);

        if (isCracSupported()) {
            CracSupport.register(appender);
        }

        return appender;
    }

    // for testing
    AppenderConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void append(LogEvent event) {
        try {
            byte[] data = LogEventEncoder.encodeLogEvent(event, this.configuration, this.pid);
            this.journalSocket.send(data);
        } catch (IOException e) {
            LOGGER.error("Error encoding LogEvent data", e);
        }
    }

    private static String getCurrentProcessPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    private static boolean isCracSupported() {
        try {
            Class.forName("org.crac.Resource");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    private static final class CracSupport {
        static void register(SystemdJournalAppender appender) {
            org.crac.Core.getGlobalContext().register(new org.crac.Resource() {
                @Override
                public void beforeCheckpoint(org.crac.Context<?> context) {
                    appender.journalSocket.close();
                }

                @Override
                public void afterRestore(org.crac.Context<? extends org.crac.Resource> context) throws Exception {
                    appender.journalSocket.init();
                    // recalculate PID, restored process will have a different one
                    appender.pid = getCurrentProcessPid();
                }
            });
        }
    }
}

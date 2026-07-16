[![Build Status](https://travis-ci.org/bwaldvogel/log4j-systemd-journal-appender.png?branch=master)](https://travis-ci.org/bwaldvogel/log4j-systemd-journal-appender)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.bwaldvogel/log4j-systemd-journal-appender/badge.svg)](http://maven-badges.herokuapp.com/maven-central/de.bwaldvogel/log4j-systemd-journal-appender)

[Log4j][log4j] appender that logs event meta data such as the timestamp, the logger name, the exception stacktrace, [ThreadContext (aka MDC)][thread-context] or the Java thread name to [fields][systemd-journal-fields] in [systemd journal][systemd-journal] (aka "the Journal") .

Read Lennart Poettering's blog post [systemd for Developers III][systemd-for-developers] if you are not familar with [systemd journal][systemd-journal].

## Usage with Log4j 2.x ##
Add the following Maven dependency to your project:

```xml
<dependency>
	<groupId>de.bwaldvogel</groupId>
	<artifactId>log4j-systemd-journal-appender</artifactId>
	<version>2.4.0</version>
	<scope>runtime</scope>
</dependency>
```

## Usage with Log4j 1.x ##

See the [`1.x` branch][1.x-branch] of this project.

### Runtime dependencies ###

- Java 7 or later
- Linux with systemd-journal
- Log4j 2.x

**Note:**

JNA requires execute permissions in `java.io.tmpdir` (which defaults to `/tmp`).
For example, if the folder is mounted with "`noexec`" for security reasons, you need to define a different temporary directory for JNA:

    -Djna.tmpdir=/tmp-folder/with/exec/permissions

## Configuration

`name` is the only **mandatory** attribute (as with any Log4j appender). Every other property
below is optional and falls back to its listed default when omitted.

Property name         | Mandatory | Default           | Type    | Description
--------------------- | --------- | ----------------- | ------- | -----------
`name`                | **yes**   | —                 | String  | The appender's name, used to reference it from a `<Logger>`'s `<AppenderRef>`. Also emitted as the `LOG4J_APPENDER` field when `logAppenderName` is enabled.
`ignoreExceptions`    | no        | true              | boolean | Determines whether exceptions while appending are suppressed (standard Log4j `AbstractAppender` behavior) or allowed to propagate.
`logSource`           | no        | false             | boolean | Determines whether the log locations are logged. Note that there is a performance overhead when switched on. The data is logged in standard systemd journal fields `CODE_FILE`, `CODE_LINE` and `CODE_FUNC`.
`logStacktrace`       | no        | true              | boolean | Determines whether the full exception stack trace is logged. This data is logged in the user field `STACKTRACE`.
`logThreadName`       | no        | true              | boolean | Determines whether the thread name is logged. This data is logged in the user field `THREAD_NAME`.
`logLoggerName`       | no        | true              | boolean | Determines whether the logger name is logged. This data is logged in the user field `LOG4J_LOGGER` (or `<logLoggerAppName>_LOGGER` if `logLoggerAppName` is set).
`logAppenderName`     | no        | true              | boolean | Determines whether the appender name is logged. This data is logged in the user field `LOG4J_APPENDER`.
`logLoggerAppName`    | no        | null              | String  | If set, the logger name is logged under the `<logLoggerAppName>_LOGGER` field instead of the default `LOG4J_LOGGER`.
`logThreadContext`    | no        | true              | boolean | Determines whether the [thread context][thread-context] is logged. Each key/value pair is logged as user field with the `threadContextPrefix` prefix.
`threadContextPrefix` | no        | `THREAD_CONTEXT_` | String  | Determines how [thread context][thread-context] keys should be prefixed when `logThreadContext` is set to true. Note that keys need to match the regex pattern `[A-Z0-9_]+` and are normalized otherwise.
`syslogIdentifier`    | no        | null              | String  | This data is logged in the user field `SYSLOG_IDENTIFIER`.  If this is not set, the underlying system will use the command name (usually `java`) instead.
`syslogFacility`      | no        | none (disabled)   | int     | If set, must be between 0 and 23 (see `syslog.h`); logged in the user field `SYSLOG_FACILITY`. An out-of-range value fails appender creation with a clear error rather than being silently ignored.

## Example ##

### `log4j2.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="INFO" packages="de.bwaldvogel.log4j">
    <Appenders>
        <Console name="console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" />
        </Console>
        <SystemdJournal name="journal" logStacktrace="true" logSource="false" />
    </Appenders>
    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="console" />
            <AppenderRef ref="journal" />
        </Root>
    </Loggers>
</Configuration>
```

This will tell Log4j to log to [systemd journal][systemd-journal] as well as to stdout (console).
Note that a layout is optional for `SystemdJournal`.
This is because meta data of a log event such as the timestamp, the logger name or the Java thread name are mapped to [systemd-journal fields][systemd-journal-fields] and need not be rendered into a string that loses all the semantic information.

### `YourExample.java`
```java
import org.apache.logging.log4j.*;

class YourExample {

    private static Logger logger = LogManager.getLogger(YourExample.class);

    public static void main(String[] args) {
        ThreadContext.put("MY_KEY", "some value");
        logger.info("this is an example");
    }
}
```

Running this sample class will log a message to journald:

### Systemd Journal

```
# journalctl -n
Okt 13 21:26:00 myhost java[2370]: this is an example
```

Use `journalctl -o verbose` to show all fields:

```
# journalctl -o verbose -n
Di 2015-09-29 21:07:05.850017 CEST [s=45e0…;i=984;b=c257…;m=1833…;t=520e…;x=3e1e…]
    PRIORITY=6
    _TRANSPORT=journal
    _UID=1000
    _GID=1000
    _CAP_EFFECTIVE=0
    _SYSTEMD_OWNER_UID=1000
    _SYSTEMD_SLICE=user-1000.slice
    _MACHINE_ID=4abc6d…
    _HOSTNAME=myhost
    _SYSTEMD_CGROUP=/user.slice/user-1000.slice/session-2.scope
    _SYSTEMD_SESSION=2
    _SYSTEMD_UNIT=session-2.scope
    _BOOT_ID=c257f8…
    THREAD_NAME=main
    LOG4J_LOGGER=de.bwaldvogel.log4j.SystemdJournalAppenderIntegrationTest
    _COMM=java
    _EXE=/usr/bin/java
    MESSAGE=this is a test message with a MDC
    CODE_FILE=SystemdJournalAppenderIntegrationTest.java
    CODE_FUNC=testMessageWithMDC
    CODE_LINE=36
    THREAD_CONTEXT_SOME_KEY1=some value %d
    THREAD_CONTEXT_SOME_KEY2=some other value with unicode: →←üöß
    SYSLOG_IDENTIFIER=log4j2-test
    LOG4J_APPENDER=Journal
    _PID=8224
    _CMDLINE=/usr/bin/java …
    _SOURCE_REALTIME_TIMESTAMP=1443553625850017
```

Note that the [ThreadContext][thread-context] key-value pair `{"MY_KEY": "some value"}` is automatically added as field with prefix `THREAD_CONTEXT`.

You can use the power of [systemd journal][systemd-journal] to filter for interesting messages. Example:

`journalctl CODE_FUNC=testMessageWithMDC THREAD_NAME=main` will only show messages that are logged from the Java main thread in a method called `testMessageWithMDC`.

## Related Work ##

* [logback-journal][logback-journal]
	* Systemd Journal appender for Logback

[1.x-branch]: https://github.com/bwaldvogel/log4j-systemd-journal-appender/tree/1.x
[log4j]: http://logging.apache.org/log4j/2.x/
[thread-context]: http://logging.apache.org/log4j/2.x/manual/thread-context.html
[systemd-for-developers]: http://0pointer.de/blog/projects/journal-submit.html
[systemd-journal]: http://www.freedesktop.org/software/systemd/man/systemd-journald.service.html
[systemd-journal-fields]: http://www.freedesktop.org/software/systemd/man/systemd.journal-fields.html
[logback-journal]: https://github.com/gnieh/logback-journal

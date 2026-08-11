package com.v2board.api.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import com.v2board.api.service.SystemLogService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Attach {@link DbErrorAppender} to the root logger at ERROR threshold after context is ready.
 */
@Component
public class DbErrorAppenderRegistrar {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(DbErrorAppenderRegistrar.class);

    public static final String APPENDER_NAME = "DB_ERROR_V2_LOG";

    @Autowired
    private SystemLogService systemLogService;

    @EventListener(ApplicationReadyEvent.class)
    public void attach() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            log.warn("DbErrorAppender not attached: ILoggerFactory is not Logback");
            return;
        }
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(APPENDER_NAME) != null) {
            return;
        }
        DbErrorAppender appender = new DbErrorAppender(systemLogService);
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel("ERROR");
        filter.setContext(context);
        filter.start();
        appender.addFilter(filter);
        appender.start();
        root.addAppender(appender);
        log.info("DbErrorAppender attached to root logger (ERROR+)");
    }
}

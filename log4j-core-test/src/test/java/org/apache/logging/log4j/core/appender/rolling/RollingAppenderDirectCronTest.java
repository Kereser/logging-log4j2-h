/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.core.appender.rolling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.apache.logging.log4j.core.test.junit.Named;
import org.apache.logging.log4j.test.junit.TempLoggingDir;
import org.apache.logging.log4j.test.junit.UsingStatusListener;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@UsingStatusListener
class RollingAppenderDirectCronTest {

    private static final Pattern FILE_PATTERN = Pattern.compile("test-(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})\\.log");
    private static final Pattern LINE_PATTERN = Pattern.compile("This is test message number \\d+\\.");

    @TempLoggingDir
    private Path loggingPath;

    @Test
    @LoggerContextSource
    void testAppender(final LoggerContext ctx, @Named("RollingFile") final RollingFileAppender app) throws Exception {
        final Logger logger = ctx.getLogger(RollingAppenderDirectCronTest.class);
        int msgNumber = 1;
        logger.debug("This is test message number {}.", msgNumber++);
        final RolloverDelay delay = new RolloverDelay(app.getManager());
        delay.waitForRollover();
        final File dir = new File(DIR);
        final File[] files = dir.listFiles();
        assertTrue("Directory not created", dir.exists() && files != null && files.length > 0);
        delay.reset(3);

        final int MAX_TRIES = 30;
        for (int i = 0; i < MAX_TRIES; ++i) {
            logger.debug("Adding new event {}", i);
            Thread.sleep(100);
        }
        delay.waitForRollover();
    }

    private static class RolloverDelay implements RolloverListener {
        private volatile CountDownLatch latch;

        public RolloverDelay(final RollingFileManager manager) {
            latch = new CountDownLatch(1);
            manager.addRolloverListener(this);
        }

        public void waitForRollover() {
            waitAtMost(3, TimeUnit.SECONDS).until(() -> latch.getCount() == 0);
        }

        public void reset(final int count) {
            latch = new CountDownLatch(count);
        }

        @Override
        public void rolloverTriggered(final String fileName) {}

        @Override
        public void rolloverComplete(final String fileName) {
            final Path path = Paths.get(fileName);
            final Matcher matcher = FILE_PATTERN.matcher(path.getFileName().toString());
            assertThat(matcher).as("Rolled file").matches();
            try {
                final List<String> lines = Files.readAllLines(path);
                assertThat
                assertTrue("Not enough lines in " + fileName + ":" + lines.size(), lines.size() > 0);
                assertTrue(
                        "log and file times don't match. file: " + matcher.group(1) + ", log: " + lines.get(0),
                        lines.get(0).startsWith(matcher.group(1)));
            } catch (IOException ex) {
                fail("Unable to read file " + fileName + ": " + ex.getMessage());
            }
            latch.countDown();
        }
    }
}

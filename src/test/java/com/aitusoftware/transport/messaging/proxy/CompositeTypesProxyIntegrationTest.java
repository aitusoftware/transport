/*
 * Copyright 2017 - 2018 Aitu Software Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aitusoftware.transport.messaging.proxy;

import com.aitusoftware.transport.Fixtures;
import com.aitusoftware.transport.buffer.PageCache;
import com.aitusoftware.transport.buffer.Preloader;
import com.aitusoftware.transport.memory.BufferUtil;
import com.aitusoftware.transport.messaging.ExecutionReport;
import com.aitusoftware.transport.messaging.ExecutionReportBuilder;
import com.aitusoftware.transport.messaging.OrderDetails;
import com.aitusoftware.transport.messaging.OrderDetailsBuilder;
import com.aitusoftware.transport.reader.RecordHandler;
import com.aitusoftware.transport.reader.StreamingReader;
import com.aitusoftware.transport.threads.Idlers;
import org.HdrHistogram.Histogram;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class CompositeTypesProxyIntegrationTest
{
    private static final int DEFAULT_CACHED_PAGES = 32;
    private final Path tempDir = Fixtures.tempDirectory();
    private PageCache pageCache;
    private PublisherFactory factory;
    private final SubscriberFactory subscriberFactory = new SubscriberFactory();

    @Before
    public void setUp() throws Exception
    {
        pageCache = PageCache.create(tempDir, 16777216);
        factory = new PublisherFactory(pageCache);
    }

    @Ignore
    @Test
    public void speedTest() throws Exception
    {
        final long startMappedBufferCount = BufferUtil.mappedBufferCount();
        final Thread preloader = new Thread(new Preloader(pageCache)::execute);
        preloader.start();
        final Thread unmapper = new Thread(pageCache.getUnmapper()::execute);
        unmapper.start();
        final CompositeTopic proxy = factory.getPublisherProxy(CompositeTopic.class);
        final AtomicInteger receivedMessages = new AtomicInteger();
        final Histogram histogram = new Histogram(1_000_000, 3);
        final Subscriber<CompositeTopic> subscriber =
                subscriberFactory.getSubscriber(CompositeTopic.class,
                        (id, orderDetails, executionReport, venueResponse, timestamp) -> {
                            receivedMessages.incrementAndGet();
                            if (receivedMessages.get() > 5_000_000)
                            {
                                histogram.recordValue(Math.min(1_000_000, (System.nanoTime() - executionReport.timestamp()) / 1000));
                            }
                        });

        final OrderDetailsBuilder orderDetails = new OrderDetailsBuilder();
        final ExecutionReportBuilder executionReport = new ExecutionReportBuilder();
        final StreamingReader streamingReader = new StreamingReader(pageCache, new RecordHandler()
        {
            @Override
            public void onRecord(final ByteBuffer data, final int pageNumber, final int position)
            {
                data.getInt();
                subscriber.onRecord(data, pageNumber, position);
            }
        }, true, Idlers.staticPause(1, TimeUnit.MILLISECONDS));

        final Thread receiver = new Thread(streamingReader::process);
        receiver.start();
        final String venueResponse = "response_";
        setFields(orderDetails, executionReport);

        final int messageCount = 50_000_000;
        final long startNanos = System.nanoTime();
        for (int j = 0; j < messageCount; j++)
        {
            executionReport.timestamp(System.nanoTime());
            proxy.sendData(j, orderDetails, executionReport, venueResponse, 7 * 7);

            final long pauseUntil = System.nanoTime() + 1_000L;
            while (System.nanoTime() < pauseUntil)
            {
                // spin
            }
        }

        while (receivedMessages.get() != messageCount)
        {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }

        final long durationNanos = System.nanoTime() - startNanos;
        final double mps = messageCount / (durationNanos / (double)TimeUnit.SECONDS.toNanos(1));

        histogram.outputPercentileDistribution(System.out, 1d);
        receiver.interrupt();
        receiver.join();
        preloader.interrupt();
        preloader.join();

        System.out.printf("%n%nAvg throughput %.2f/sec%n", mps);
        System.out.printf("Mean: %.2f, 50%%: %d, 90%%: %d, 99%%: %d, 99.99%%: %d%n",
                histogram.getMean(), histogram.getValueAtPercentile(50d),
                histogram.getValueAtPercentile(90d), histogram.getValueAtPercentile(99d),
                histogram.getValueAtPercentile(99.99d));

        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4L));

        unmapper.interrupt();
        unmapper.join();

        waitForMemoryCleanup(startMappedBufferCount);
        final long mappedBufferDelta = BufferUtil.mappedBufferCount() - startMappedBufferCount;
        assertTrue(String.format("%d buffers remain mapped", mappedBufferDelta),
                Math.abs(mappedBufferDelta) < DEFAULT_CACHED_PAGES);

    }

    @Test
    public void shouldSendMessages() throws Exception
    {
        final CompositeTopic proxy = factory.getPublisherProxy(CompositeTopic.class);
        final List<ArgumentContainer> receivedMessages = new CopyOnWriteArrayList<>();
        final List<ArgumentContainer> sentMessages = new LinkedList<>();
        final Subscriber<CompositeTopic> subscriber =
                subscriberFactory.getSubscriber(CompositeTopic.class,
                        (id, orderDetails, executionReport, venueResponse, timestamp) -> {

                            receivedMessages.add(new ArgumentContainer(
                                    id, orderDetails.heapCopy(), executionReport.heapCopy(), venueResponse.toString(), timestamp));
                        });

        for (int i = 0; i < 1; i++)
        {
            final OrderDetailsBuilder orderDetails = new OrderDetailsBuilder();
            final ExecutionReportBuilder executionReport = new ExecutionReportBuilder();
            orderDetails.reset();
            orderDetails.quantity(3 * i);
            orderDetails.price(5 * i);
            orderDetails.orderId(11 * i);
            orderDetails.setIdentifier("order_" + i);

            executionReport.reset();
            executionReport.isBid((i & 1) == 0);
            executionReport.orderId("exec_order_" + i);
            executionReport.statusMessage("status_" + i);
            executionReport.timestamp(System.nanoTime());
            executionReport.quantity(17 * i);
            executionReport.price(19 * i);

            final String venueResponse = "response_" + i;
            final int timestamp = i * 7;
            proxy.sendData(i, orderDetails, executionReport, venueResponse, timestamp);

            sentMessages.add(new ArgumentContainer(i, orderDetails, executionReport, venueResponse, timestamp));
        }

        new StreamingReader(pageCache, new RecordHandler()
        {
            @Override
            public void onRecord(final ByteBuffer data, final int pageNumber, final int position)
            {
                data.getInt();
                subscriber.onRecord(data, pageNumber, position);
            }
        }, false, Idlers.staticPause(1, TimeUnit.MILLISECONDS)).process();

        while (receivedMessages.size() != sentMessages.size())
        {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }

        for (int i = 0; i < sentMessages.size(); i++)
        {
            final ArgumentContainer sent = sentMessages.get(i);
            final ArgumentContainer received = receivedMessages.get(i);

            assertThat(sent.id, is(received.id));
            assertThat(sent.timestamp, is(received.timestamp));
            assertThat(sent.venueResponse.toString(), is(received.venueResponse.toString()));

            assertThat(sent.orderDetails.getIdentifier().toString(),
                    is(received.orderDetails.getIdentifier().toString()));
            assertThat(sent.orderDetails.orderId(),
                    is(received.orderDetails.orderId()));
            assertThat(sent.orderDetails.price(),
                    is(received.orderDetails.price()));
            assertThat(sent.orderDetails.quantity(),
                    is(received.orderDetails.quantity()));

            assertThat(sent.executionReport.isBid(),
                    is(received.executionReport.isBid()));
            assertThat(sent.executionReport.orderId().toString(),
                    is(received.executionReport.orderId().toString()));
            assertThat(sent.executionReport.price(),
                    is(received.executionReport.price()));
            assertThat(sent.executionReport.quantity(),
                    is(received.executionReport.quantity()));
            assertThat(sent.executionReport.timestamp(),
                    is(received.executionReport.timestamp()));
            assertThat(sent.executionReport.statusMessage().toString(),
                    is(received.executionReport.statusMessage().toString()));
        }
    }

    private void waitForMemoryCleanup(final long startMappedBufferCount) throws InterruptedException
    {
        final long timeoutAt = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < timeoutAt)
        {
            if (BufferUtil.mappedBufferCount() - startMappedBufferCount > DEFAULT_CACHED_PAGES)
            {
                Thread.sleep(1_000L);
            }
        }
    }

    private void setFields(final OrderDetailsBuilder orderDetails, final ExecutionReportBuilder executionReport)
    {
        orderDetails.reset();
        orderDetails.quantity(3 * 42);
        orderDetails.price(5 * 42);
        orderDetails.orderId(11 * 42);
        orderDetails.setIdentifier("order_");
        executionReport.reset();
        executionReport.isBid((42 & 1) == 0);
        executionReport.orderId("exec_order_");
        executionReport.statusMessage("status_");
        executionReport.quantity(17 * 42);
        executionReport.price(19 * 42);
    }


    private static final class ArgumentContainer
    {
        private final long id;
        private final OrderDetails orderDetails;
        private final ExecutionReport executionReport;
        private final CharSequence venueResponse;
        private final long timestamp;

        ArgumentContainer(final long id, final OrderDetails orderDetails, final ExecutionReport executionReport, final CharSequence venueResponse, final long timestamp)
        {
            this.id = id;
            this.orderDetails = orderDetails;
            this.executionReport = executionReport;
            this.venueResponse = venueResponse;
            this.timestamp = timestamp;
        }
    }
}

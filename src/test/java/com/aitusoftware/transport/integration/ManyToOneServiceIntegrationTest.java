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
package com.aitusoftware.transport.integration;

import com.aitusoftware.transport.Fixtures;
import com.aitusoftware.transport.factory.Media;
import com.aitusoftware.transport.factory.Service;
import com.aitusoftware.transport.factory.ServiceFactory;
import com.aitusoftware.transport.factory.SubscriberDefinition;
import com.aitusoftware.transport.factory.SubscriberThreading;
import com.aitusoftware.transport.net.AddressSpace;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aitusoftware.transport.Fixtures.testIdlerFactory;

public final class ManyToOneServiceIntegrationTest
{
    private static final int PUBLISHING_SERVICE_COUNT = 10;
    private final TraderBot[] publishers =  new TraderBot[PUBLISHING_SERVICE_COUNT];
    private final CountingTradeNotifications tradeNotifications = new CountingTradeNotifications();
    private final Media media = Media.TCP;

    @Before
    public void setUp() throws Exception
    {
        final Path orderGatewayPath = Fixtures.tempDirectory();

        final ServerSocketChannel traderBotListenAddr = ServerSocketChannel.open();
        traderBotListenAddr.configureBlocking(false);

        traderBotListenAddr.bind(null);

        final AddressSpace testAddressSpace = new SingleSocketAddressSpace(traderBotListenAddr);

        for (int i = 0; i < PUBLISHING_SERVICE_COUNT; i++)
        {
            final Path traderBotPath = Fixtures.tempDirectory();
            final ServiceFactory traderBotServiceFactory = new ServiceFactory(traderBotPath,
                    new FixedServerSocketFactory(ServerSocketChannel.open()), testAddressSpace, testIdlerFactory(),
                    SubscriberThreading.SINGLE_THREADED, Fixtures.testingIdlerConfig());
            publishers[i] = new TraderBot(traderBotServiceFactory.createPublisher(OrderNotifications.class, media));
            traderBotServiceFactory.create().start();
        }

        final ServiceFactory orderGatewayServiceFactory = new ServiceFactory(orderGatewayPath,
                new FixedServerSocketFactory(traderBotListenAddr), testAddressSpace, testIdlerFactory(),
                SubscriberThreading.SINGLE_THREADED, Fixtures.testingIdlerConfig());
        final OrderGateway orderGateway = new OrderGateway(tradeNotifications);
        orderGatewayServiceFactory.registerRemoteSubscriber(
                new SubscriberDefinition<>(OrderNotifications.class, orderGateway, media));
        final Service orderGatewayService = orderGatewayServiceFactory.create();
        orderGatewayService.start();
    }

    @Test
    public void shouldAcceptMultipleInboundConnections() throws Exception
    {
        for (int i = 0; i < 40; i++)
        {
            for (int i1 = 0; i1 < publishers.length; i1++)
            {
                final TraderBot publisher = publishers[i1];
                publisher.onBid("test-" + i + "-" + i1, i, 3.14D, i1);
            }
        }

        if (!tradeNotifications.latch.await(5, TimeUnit.SECONDS))
        {
            Assert.fail(String.format("Did not receive expected number of messages. Number remaining: %d%n",
                    tradeNotifications.latch.getCount()));
        }
    }

    private static final class CountingTradeNotifications implements TradeNotifications
    {
        private final CountDownLatch latch = new CountDownLatch(30);

        @Override
        public void onOrderAccepted(final CharSequence symbol, final CharSequence orderId, final boolean isBid, final long matchedQuantity,
                                    final long remainingQuantity, final double price, final int ecnId)
        {
            latch.countDown();
        }

        @Override
        public void onOrderRejected(final CharSequence symbol, final CharSequence orderId, final int ecnId, final int rejectionReason)
        {
            latch.countDown();
        }
    }

    private static final class SingleSocketAddressSpace implements AddressSpace
    {
        private final ServerSocketChannel channel;

        SingleSocketAddressSpace(final ServerSocketChannel channel)
        {
            this.channel = channel;
        }

        @Override
        public int portOf(final Class<?> topicClass)
        {
            return channel.socket().getLocalPort();
        }

        @Override
        public String hostOf(final Class<?> topicClass)
        {
            return "127.0.0.1";
        }
    }
}

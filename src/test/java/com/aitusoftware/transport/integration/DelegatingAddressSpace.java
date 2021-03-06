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

import com.aitusoftware.transport.net.AddressSpace;

final class DelegatingAddressSpace implements AddressSpace
{
    private final AddressSpace delegate;
    private final int traderBotListenPort;
    private final int orderGatewayListenPort;

    DelegatingAddressSpace(final AddressSpace delegate,
                           final int traderBotListenPort,
                           final int orderGatewayListenPort)
    {
        this.delegate = delegate;
        this.traderBotListenPort = traderBotListenPort;
        this.orderGatewayListenPort = orderGatewayListenPort;
    }

    @Override
    public int portOf(final Class<?> topicClass)
    {
        if (MarketData.class.isAssignableFrom(topicClass) ||
                MarketNews.class.isAssignableFrom(topicClass) ||
                TradeNotifications.class.isAssignableFrom(topicClass))
        {
            return traderBotListenPort;
        }
        return orderGatewayListenPort;
    }

    @Override
    public String hostOf(final Class<?> topicClass)
    {
        return delegate.hostOf(topicClass);
    }
}

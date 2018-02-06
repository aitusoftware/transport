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

public final class OrderGateway implements OrderNotifications
{
    private final TradeNotifications tradeNotifications;

    public OrderGateway(final TradeNotifications tradeNotifications)
    {
        this.tradeNotifications = tradeNotifications;
    }

    @Override
    public void limitOrder(
            final CharSequence symbol, final CharSequence orderId,
            final boolean isBid, final long quantity, final double price, final int ecnId)
    {
        tradeNotifications.onOrderAccepted(symbol, orderId, isBid, quantity,
                0, price, ecnId);
    }

    @Override
    public void marketOrder(
            final CharSequence symbol, final CharSequence orderId,
            final boolean isBid, final long quantity, final int ecnId)
    {
        tradeNotifications.onOrderAccepted(symbol, orderId, isBid, quantity,
                0, Double.MIN_VALUE, ecnId);
    }

    @Override
    public void cancelOrder(final CharSequence orderId, final int ecnId)
    {
        // no-op
    }
}

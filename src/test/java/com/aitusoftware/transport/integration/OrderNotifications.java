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

import com.aitusoftware.transport.messaging.Topic;

@Topic(listenAddress = "127.0.0.1", port = 12003)
public interface OrderNotifications
{
    void limitOrder(
            final CharSequence symbol, final CharSequence orderId,
            final boolean isBid, final long quantity,
            final double price, final int ecnId);
    void marketOrder(
            final CharSequence symbol, final CharSequence orderId,
            final boolean isBid, final long quantity,
            final int ecnId);
    void cancelOrder(
            final CharSequence orderId, final int ecnId);
}
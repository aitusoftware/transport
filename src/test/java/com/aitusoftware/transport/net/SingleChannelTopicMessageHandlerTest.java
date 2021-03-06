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
package com.aitusoftware.transport.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

import static com.aitusoftware.transport.Action.executeQuietly;
import static org.junit.Assert.assertArrayEquals;

public class SingleChannelTopicMessageHandlerTest
{
    private static final byte[] PAYLOAD = new byte[] {(byte) 4, 3, 1, 1, 0};
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerSocketChannel server;
    private SingleChannelTopicMessageHandler messageHandler;

    @Before
    public void setUp() throws Exception
    {
        server = ServerSocketChannel.open();
        server.bind(null);
        final TopicToChannelMapper mapper = new TopicToChannelMapper(createChannel());
        messageHandler = new SingleChannelTopicMessageHandler(mapper);
    }

    @After
    public void tearDown() throws Exception
    {
        executeQuietly(server::close);
        executeQuietly(executor::shutdownNow);
    }

    @Test
    public void shouldSendData() throws InterruptedException, ExecutionException, TimeoutException
    {
        final Future<byte[]> receiver = startReceiver();

        messageHandler.onTopicMessage(17, ByteBuffer.wrap(PAYLOAD));

        assertArrayEquals(receiver.get(1, TimeUnit.SECONDS), PAYLOAD);
    }

    private Future<byte[]> startReceiver()
    {
        return executor.submit(() -> {
            try
            {
                final SocketChannel client = server.accept();
                client.configureBlocking(true);
                final ByteBuffer buffer = ByteBuffer.allocateDirect(64);
                client.read(buffer);
                buffer.flip();
                final byte[] payload = new byte[buffer.getInt()];
                buffer.get(payload);
                return payload;
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        });
    }

    private IntFunction<SocketChannel> createChannel()
    {
        return i -> {
            try
            {
                return SocketChannel.open(server.getLocalAddress());
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        };
    }
}
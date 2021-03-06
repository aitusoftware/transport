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

import com.aitusoftware.transport.threads.SingleThreaded;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;

@SingleThreaded
public final class MultiChannelTopicMessageHandler implements TopicMessageHandler
{
    private final ByteBuffer[] srcs = new ByteBuffer[2];
    private final TopicToChannelMapper channelMapper;
    private final int numberOfConnections;
    private final ByteBuffer lengthBuffer;

    public MultiChannelTopicMessageHandler(
            final TopicToChannelMapper channelMapper, final int numberOfConnections)
    {
        this.channelMapper = channelMapper;
        this.numberOfConnections = numberOfConnections;
        lengthBuffer = ByteBuffer.allocateDirect(4);
        srcs[0] = lengthBuffer;
    }

    @Override
    public void onTopicMessage(final int topicId, final ByteBuffer data)
    {
        srcs[1] = data;
        lengthBuffer.clear();
        // TODO should be able to determine length from record header
        lengthBuffer.putInt(0, data.remaining());
        for (int i = 0; i < numberOfConnections; i++)
        {
            lengthBuffer.clear();
            data.mark();
            writeToChannel(data, i);

            data.reset();
        }
    }

    private void writeToChannel(final ByteBuffer data, final int index)
    {
        do
        {
            try
            {
                final GatheringByteChannel channel = channelMapper.forTopic(index);
                if (channel == null)
                {
                    // TODO should be handled by reconnect logic
                    return;
                }
                channel.write(srcs);
            }
            catch (RuntimeException | IOException e)
            {
                // TODO buffer data
                channelMapper.reconnectChannel(index);
                return;
            }
        }
        while ((data.remaining() != 0 || lengthBuffer.remaining() != 0) &&
                !Thread.currentThread().isInterrupted());
    }
}

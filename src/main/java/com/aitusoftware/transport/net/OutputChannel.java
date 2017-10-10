package com.aitusoftware.transport.net;

import com.aitusoftware.transport.reader.RecordHandler;
import org.agrona.collections.Int2ObjectHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public final class OutputChannel implements RecordHandler
{
    private final TopicToChannelMapper channelMapper;
    private final Int2ObjectHashMap<ByteBuffer> enqueuedData = new Int2ObjectHashMap<>();
    private final ByteBuffer[] srcs = new ByteBuffer[2];
    private final ByteBuffer lengthBuffer;

    public OutputChannel(final TopicToChannelMapper channelMapper)
    {
        this.channelMapper = channelMapper;
        lengthBuffer = ByteBuffer.allocateDirect(4);
        srcs[0] = lengthBuffer;
    }

    @Override
    public void onRecord(final ByteBuffer data, final int pageNumber, final int position)
    {
        final int topicId = data.getInt(data.position());

        srcs[1] = data;
        lengthBuffer.clear();
        // TODO should be able to determine length from record header
        lengthBuffer.putInt(0, data.remaining());

        final SocketChannel channel = channelMapper.forTopic(topicId);
        while (data.remaining() != 0 || lengthBuffer.remaining() != 0)
        {
            try
            {
                channel.write(srcs);
            }
            catch (IOException e)
            {
                // TODO buffer data
                channelMapper.reconnectChannel(topicId);
            }
        }
    }
}

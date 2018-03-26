package org.logstash.beats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;


public class BeatsParserTest {
    public final static ObjectMapper MAPPER = new ObjectMapper().registerModule(new AfterburnerModule());

    private V1Batch v1Batch;
    private V1Batch v2Batch;
    private final int numberOfMessage = 20;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private class SpyListener implements IMessageListener {
        private boolean onNewConnectionCalled = false;
        private boolean onNewMessageCalled = false;
        private boolean onConnectionCloseCalled = false;
        private boolean onExceptionCalled = false;
        private final List<Message> lastMessages = new ArrayList<Message>();

        @Override
        public void onNewMessage(ChannelHandlerContext ctx, Message message) {
            onNewMessageCalled = true;
            lastMessages.add(message);
            message.getData();
        }

        @Override
        public void onNewConnection(ChannelHandlerContext ctx) {
            ctx.channel().attr(ConnectionHandler.CHANNEL_SEND_KEEP_ALIVE).set(new AtomicBoolean(false));
            onNewConnectionCalled = true;
        }

        @Override
        public void onConnectionClose(ChannelHandlerContext ctx) {
            onConnectionCloseCalled = true;
        }

        @Override
        public void onException(ChannelHandlerContext ctx, Throwable cause) { onExceptionCalled = true; }

        @Override
        public void onChannelInitializeException(ChannelHandlerContext ctx, Throwable cause) {
        }

        public boolean isOnNewConnectionCalled() {
            return onNewConnectionCalled;
        }

        public boolean isOnNewMessageCalled() {
            return onNewMessageCalled;
        }

        public boolean isOnConnectionCloseCalled() {
            return onConnectionCloseCalled;
        }

        public List<Message> getLastMessages() {
            return lastMessages;
        }

        public boolean isOnExceptionCalled() {
            return onExceptionCalled;
        }
    }

    @Before
    public void setup() throws Exception{
        this.v1Batch = new V1Batch(Protocol.VERSION_1);

        for(int i = 1; i <= numberOfMessage; i++) {
            Map map = new HashMap<String, String>();
            map.put("line", "Another world");
            map.put("from", "Little big Adventure");

            Message message = new Message(i, map);
            this.v1Batch.addMessage(message);
        }

        this.v2Batch = new V1Batch(Protocol.VERSION_2);

        for(int i = 1; i <= numberOfMessage; i++) {
            Map map = new HashMap<String, String>();
            map.put("line", "Another world");
            map.put("from", "Little big Adventure");

            Message message = new Message(i, map);
            this.v2Batch.addMessage(message);
        }

//        this.v2Batch = new V1Batch(Protocol.VERSION_2);

//        for(int i = 1; i <= numberOfMessage; i++) {
//            Map map = new HashMap<String, String>();
//            map.put("line", "Another world");
//            map.put("from", "Little big Adventure");
//            ByteBuf bytebuf = Unpooled.wrappedBuffer(MAPPER.writeValueAsBytes(map));
//            this.v2Batch.addMessage(i, bytebuf, bytebuf.readableBytes());
//        }

    }

    @Test
    public void testEncodingDecodingJson() {
//        List<Message> decodedBatch = decodeBatch(v2Batch);
//        assertMessages(v1Batch, decodedBatch);
    }

    @Test
    public void testCompressedEncodingDecodingJson() {
//        List<Message> decodedBatch = decodeCompressedBatch(v2Batch);
//        assertMessages(v1Batch, decodedBatch);
    }

    @Test
    public void testEncodingDecodingFields() {
//        List<Message> decodedBatch = decodeBatch(v2Batch);
//        assertMessages(v1Batch, decodedBatch);
    }

    @Test
    public void testEncodingDecodingFieldWithUTFCharacters() throws Exception {
        V1Batch v2Batch = new V1Batch(Protocol.VERSION_2);
        ByteBuf payload = Unpooled.buffer();

        // Generate Data with Keys and String with UTF-8
        for(int i = 0; i < numberOfMessage; i++) {


            Map map = new HashMap<String, String>();
            map.put("étoile", "mystère");
            map.put("from", "ÉeèAççï");
            v2Batch.addMessage(new Message(i, map));
        }

        SpyListener spyListener = new SpyListener();
        EmbeddedChannel channel = new EmbeddedChannel(new CompressedBatchEncoder(), new BeatsParser(), new BeatsMessageHandler(spyListener));


//            List<Message> decodedBatch =
                    decodeBatch(v2Batch, channel);
            assertMessages(v2Batch, spyListener.getLastMessages());

    }

    @Test
    public void testV1EncodingDecodingFieldWithUTFCharacters() {
        V1Batch batch = new V1Batch(Protocol.VERSION_1);

        // Generate Data with Keys and String with UTF-8
        for(int i = 0; i < numberOfMessage; i++) {

            Map map = new HashMap<String, String>();
            map.put("étoile", "mystère");
            map.put("from", "ÉeèAççï");

            Message message = new Message(i + 1, map);
            batch.addMessage(message);
        }
        SpyListener spyListener = new SpyListener();
        decodeBatch(batch, spyListener);
        assertMessages(batch, spyListener.getLastMessages());
    }

    @Test
    public void testCompressedEncodingDecodingFields() {
        List<Message> decodedBatch = decodeCompressedBatch(v1Batch);
        assertEquals(decodedBatch.size(), numberOfMessage);
        assertMessages(this.v1Batch, decodedBatch);
    }

    @Test
    public void testShouldNotCrashOnGarbageData() {
        thrown.expectCause(isA(BeatsParser.InvalidFrameProtocolException.class));

        byte[] n = new byte[10000];
        new Random().nextBytes(n);
        ByteBuf randomBufferData = Unpooled.wrappedBuffer(n);

        sendPayloadToParser(randomBufferData);
    }


    @Test
    public void testNegativeJsonPayloadShouldRaiseAnException() throws JsonProcessingException {
        sendInvalidJSonPayload(-1);
    }

    @Test
    public void testZeroSizeJsonPayloadShouldRaiseAnException() throws JsonProcessingException {
        sendInvalidJSonPayload(0);
    }

    @Test
    public void testNegativeFieldsCountShouldRaiseAnException() {
        sendInvalidV1Payload(-1);
    }

    @Test
    public void testZeroFieldsCountShouldRaiseAnException() {
        sendInvalidV1Payload(0);
    }

    private void sendInvalidV1Payload(int size) {
        thrown.expectCause(isA(BeatsParser.InvalidFrameProtocolException.class));
        thrown.expectMessage("Invalid number of fields, received: " + size);

        ByteBuf payload = Unpooled.buffer();

        payload.writeByte(Protocol.VERSION_1);
        payload.writeByte('W');
        payload.writeInt(1);
        payload.writeByte(Protocol.VERSION_1);
        payload.writeByte('D');
        payload.writeInt(1);
        payload.writeInt(size);

        byte[] key = "message".getBytes();
        byte[] value = "Hola".getBytes();

        payload.writeInt(key.length);
        payload.writeBytes(key);
        payload.writeInt(value.length);
        payload.writeBytes(value);

        sendPayloadToParser(payload);
    }

    private void sendInvalidJSonPayload(int size) throws JsonProcessingException {
        thrown.expectCause(isA(BeatsParser.InvalidFrameProtocolException.class));
        thrown.expectMessage("Invalid json length, received: " + size);

        Map mapData = Collections.singletonMap("message", "hola");

        ByteBuf payload = Unpooled.buffer();

        payload.writeByte(Protocol.VERSION_2);
        payload.writeByte('W');
        payload.writeInt(1);
        payload.writeByte(Protocol.VERSION_2);
        payload.writeByte('J');
        payload.writeInt(1);
        payload.writeInt(size);

        byte[] json = MAPPER.writeValueAsBytes(mapData);
        payload.writeBytes(json);

        sendPayloadToParser(payload);
    }

    private void sendPayloadToParser(ByteBuf payload) {
        EmbeddedChannel channel = new EmbeddedChannel(new BeatsParser());
        channel.writeOutbound(payload);
        Object o = channel.readOutbound();
        channel.writeInbound(o);
    }

    private void assertMessages(Batch expected, List<Message> actual) {

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());

        int i = 0;
        Iterator<Message> expectedMessages = expected.iterator();
        for(Message actualMessage: actual) {
            Message expectedMessage = expectedMessages.next();
            assertEquals(expectedMessage.getSequence(), actualMessage.getSequence());
            Map expectedData = expectedMessage.getData();
            Map actualData = actualMessage.getData();

            assertEquals(expectedData.size(), actualData.size());

            for(Object entry : expectedData.entrySet()) {
                Map.Entry e = (Map.Entry) entry;
                String key = (String) e.getKey();
                String value = (String) e.getValue();

                assertEquals(value, actualData.get(key));
            }
            i++;
        }
    }

//    private Batch decodeCompressedBatch(Batch batch) {
//        EmbeddedChannel channel = new EmbeddedChannel(new CompressedBatchEncoder(), new BeatsParser());
//        channel.writeOutbound(batch);
//        Object o = channel.readOutbound();
//        channel.writeInbound(o);
//
//        return (Batch) channel.readInbound();
//    }

    private List<Message> decodeCompressedBatch(Batch batch) {
        EmbeddedChannel channel = new EmbeddedChannel(new CompressedBatchEncoder(), new BeatsParser());
        channel.writeOutbound(batch);
        Object o = channel.readOutbound();
        channel.writeInbound(o);
        List<Message> messages = new ArrayList<>();
        Message next = channel.readInbound();
        next.getData();
        while (next != null){
            messages.add(next);
            next = channel.readInbound();
        }
        return messages;
    }

    private List<Message> decodeBatch(Batch batch, EmbeddedChannel channel) {
//        EmbeddedChannel channel = new EmbeddedChannel(new CompressedBatchEncoder(), new BeatsParser());
        channel.writeOutbound(batch);
        Object o = channel.readOutbound();
        channel.writeInbound(o);
        List<Message> messages = new ArrayList<>();
        Message next = channel.readInbound();
        while (next != null){
            messages.add(next);
            next = channel.readInbound();
        }
        return messages;
    }


    private List<Message> decodeBatch(Batch batch, IMessageListener spyListener) {
        EmbeddedChannel channel = new EmbeddedChannel(new BatchEncoder(), new BeatsParser(), new BeatsMessageHandler(spyListener));
        channel.writeOutbound(batch);
        Object o = channel.readOutbound();
        channel.writeInbound(o);
        List<Message> messages = new ArrayList<>();
        Message next = channel.readInbound();
        while (next != null){
            messages.add(next);
            next = channel.readInbound();
        }
        return messages;
    }
}
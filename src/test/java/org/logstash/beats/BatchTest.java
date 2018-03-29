package org.logstash.beats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BatchTest {
    public final static ObjectMapper MAPPER = new ObjectMapper().registerModule(new AfterburnerModule());

    @Test
    public void testIsEmpty() {
        Batch batch = new Batch(Protocol.VERSION_2);
        assertTrue(batch.isEmpty());
        batch.addMessage(new Message(1, Collections.emptyMap()));
        assertFalse(batch.isEmpty());
    }

    @Test
    public void testSize() {
        Batch batch = new Batch(Protocol.VERSION_2);
        assertEquals(0, batch.size());
        ByteBuf content = messageContents();
        batch.addMessage(new Message(1, Collections.emptyMap()));
        assertEquals(1, batch.size());
    }

    @Test
    public void TestGetProtocol() {
        assertEquals(Protocol.VERSION_2, new Batch(Protocol.VERSION_2).getProtocol());
    }

    @Test
    public void TestCompleteReturnTrueWhenIReceiveTheSameAmountOfEvent() {
        Batch batch = new Batch(Protocol.VERSION_2);
        int numberOfEvent = 2;

        batch.setBatchSize(numberOfEvent);

        for(int i = 1; i <= numberOfEvent; i++) {
            ByteBuf content = messageContents();
            batch.addMessage(new Message(1, Collections.emptyMap()));
        }

        assertTrue(batch.isComplete());
    }

    @Test
    public void testBigBatch() {
        Batch batch = new Batch(Protocol.VERSION_2);
        int size = 4096;
        assertEquals(0, batch.size());
        try {
            ByteBuf content = messageContents();
            for (int i = 0; i < size; i++) {
                batch.addMessage(new Message(i, Collections.emptyMap()));
            }
            assertEquals(size, batch.size());
            int i = 0;
            for (Message message : batch) {
                assertEquals(message.getSequence(), i++);
            }
        }finally {
            batch.release();
        }
    }


    @Test
    public void TestCompleteReturnWhenTheNumberOfEventDoesntMatchBatchSize() {
        Batch batch = new Batch(Protocol.VERSION_2);
        int numberOfEvent = 2;

        batch.setBatchSize(numberOfEvent);
        ByteBuf content = messageContents();
        batch.addMessage(new Message(1, Collections.emptyMap()));


        assertFalse(batch.isComplete());
    }

    public static ByteBuf messageContents() {
        Map test = new HashMap();
        test.put("key", "value");
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(test);
            return Unpooled.wrappedBuffer(bytes);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
package org.logstash.beats;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Interface representing a Batch of {@link Message}.
 */
public class Batch implements Iterable<Message>{

    private final byte protocol;
    private int batchSize = 0;
    private List<Message> messages = new LinkedList<>();

    public Batch(byte protocol){
        this.protocol = protocol;
    }

    public byte getProtocol(){
        return protocol;
    }

    public void setBatchSize(int batchSize){
        this.batchSize = batchSize;
    }

    public int getBatchSize(){
        return batchSize;
    }

    public int size(){
        return messages.size();
    }

    public void addMessage(Message message){
        messages.add(message);
    }

    public boolean isEmpty(){
        return messages.isEmpty();
    }

    public void release(){
        messages.forEach(Message::release);
    }

    public Iterator<Message> iterator(){
        return messages.iterator();
    }

    public boolean isComplete(){
        return messages.size() == batchSize;
    }
}
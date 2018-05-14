package org.logstash.beats;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Track the number of batches from beats currently being processed.
 */
class BatchTracker {
    private AtomicInteger pendingBatches = new AtomicInteger();
    private Logger logger = LogManager.getLogger(BatchTracker.class);

    void batchStarted(){
        pendingBatches.incrementAndGet();
        if (logger.isTraceEnabled()) {
            logger.trace("Started Batch. There are now {} in flight", pendingBatches);
        }
    }

    void batchCompleted(){
        pendingBatches.decrementAndGet();
        if (logger.isTraceEnabled()) {
            logger.trace("Completed Batch.There are now {} in flight", pendingBatches);
        }
    }

    int batchesInFlight(){
        return pendingBatches.get();
    }
}

package org.logstash.beats;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Filter to start rejecting connections when 'too many' batches are currently in flight
 *
 */
public class TooManyBatchesFilter extends ChannelInboundHandlerAdapter {

    private BatchTracker tracker;
    private int maxBatches;
    private Logger logger = LogManager.getLogger(TooManyBatchesFilter.class);

    TooManyBatchesFilter(final BatchTracker tracker, final int maxBatches){
        this.tracker = tracker;
        this.maxBatches = maxBatches;
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
        rejectIfTooManyBatches(ctx);
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        rejectIfTooManyBatches(ctx);
        ctx.fireChannelActive();
    }

    private void rejectIfTooManyBatches(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().remove(this);
        int batchesInFlight = tracker.batchesInFlight();

        if (batchesInFlight >= maxBatches) {
            logger.error("Rejecting connection {} - too many batches ({}) are currently in flight", ctx, batchesInFlight);
            ctx.close();
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Accepting connection {} - enough ({}) are currently in flight", ctx, batchesInFlight);
            }
        }
    }
}

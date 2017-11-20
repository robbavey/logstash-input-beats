require "logstash/inputs/beats"
java_import "org.logstash.beats.Ack"

module LogStash module Inputs class Beats
  class AckingCallback

    attr_reader :input, :ctx, :protocol, :processing

    def initialize(input, protocol, ctx, processing)
      @input = input
      @ctx = ctx
      @protocol = protocol
      @processing = processing
    end

    def on_processed(sequence_numbezr)
      input.logger.info("processed #{sequence_numbezr.get('@sequence_number')}")
    end

    def on_failed(sequence_numbers)
      input.logger.info("failde #{sequence_numbers}")
    end

    def on_complete(batch_id, event)
      sequence_number = event.get('@sequence_number')
      input.logger.info("Callback - Batch id: #{batch_id}, Ack: #{sequence_number}")
      # ctx.write(Ack.new(protocol, sequence_number))
      # ctx.flush
      # processing.compareAndSet(false, true)
    end

  end


end;end;end
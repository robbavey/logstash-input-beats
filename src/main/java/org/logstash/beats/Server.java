package org.logstash.beats;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.logstash.netty.SslSimpleBuilder;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class Server {
    private final static Logger logger = LogManager.getLogger(Server.class);

    private final int port;
    private final String host;
    private final int beatsHeandlerThreadCount;
    private final int maxInflightBatches;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workGroup;
    private IMessageListener messageListener = new MessageListener();
    private SslSimpleBuilder sslBuilder;
    private BeatsInitializer beatsInitializer;

    private final int clientInactivityTimeoutSeconds;

    public Server(String host, int p, int timeout, int threadCount) {
        this(host, p, timeout, threadCount, threadCount);
    }

    public Server(String host, int p, int timeout, int threadCount, int maxInflightBatches) {
        this.host = host;
        port = p;
        clientInactivityTimeoutSeconds = timeout;
        beatsHeandlerThreadCount = threadCount;
        this.maxInflightBatches = maxInflightBatches;
    }

    public void enableSSL(SslSimpleBuilder builder) {
        sslBuilder = builder;
    }

    public Server listen() throws InterruptedException {
        if (bossGroup != null) {
            try {
                logger.debug("Shutting down existing boss group before starting");
                bossGroup.shutdownGracefully().sync();
            } catch (Exception e) {
                logger.error("Could not shut down boss group before starting", e);
            }
        }
        bossGroup = new NioEventLoopGroup(1);
        if (workGroup != null) {
            try {
                logger.debug("Shutting down existing worker group before starting");
                workGroup.shutdownGracefully().sync();
            } catch (Exception e) {
                logger.error("Could not shut down worker group before starting", e);
            }
        }
        workGroup = new NioEventLoopGroup();
        try {
            logger.info("Starting server on port: {}", this.port);

            beatsInitializer = new BeatsInitializer(isSslEnable(), messageListener, clientInactivityTimeoutSeconds, beatsHeandlerThreadCount);

            ServerBootstrap server = new ServerBootstrap();
            server.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_LINGER, 0) // Since the protocol doesn't support yet a remote close from the server and we don't want to have 'unclosed' socket lying around we have to use `SO_LINGER` to force the close of the socket.
                    .childHandler(beatsInitializer);

            Channel channel = server.bind(host, port).sync().channel();
            channel.closeFuture().sync();
        } finally {
            shutdown();
        }

        return this;
    }

    public void stop() {
        logger.debug("Server shutting down");
        shutdown();
        logger.debug("Server stopped");
    }

    private void shutdown(){
        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
            if (workGroup != null) {
                workGroup.shutdownGracefully().sync();
            }
            if (beatsInitializer != null) {
                beatsInitializer.shutdownEventExecutor();
            }
        } catch (InterruptedException e){
            throw new IllegalStateException(e);
        }
    }

    public void setMessageListener(IMessageListener listener) {
        messageListener = listener;
    }

    public boolean isSslEnable() {
        return this.sslBuilder != null;
    }

    private class BeatsInitializer extends ChannelInitializer<SocketChannel> {
        private final String SSL_HANDLER = "ssl-handler";
        private final String IDLESTATE_HANDLER = "idlestate-handler";
        private final String CONNECTION_HANDLER = "connection-handler";
        private final String BEATS_ACKER = "beats-acker";


        private final int DEFAULT_IDLESTATEHANDLER_THREAD = 4;
        private final int IDLESTATE_WRITER_IDLE_TIME_SECONDS = 5;

        private final EventExecutorGroup idleExecutorGroup;
        private final EventExecutorGroup beatsHandlerExecutorGroup;
        private final IMessageListener message;
        private int clientInactivityTimeoutSeconds;

        private boolean enableSSL = false;
        private BatchTracker tracker = null;

        public BeatsInitializer(Boolean secure, IMessageListener messageListener, int clientInactivityTimeoutSeconds, int beatsHandlerThread) {
            enableSSL = secure;
            this.message = messageListener;
            this.clientInactivityTimeoutSeconds = clientInactivityTimeoutSeconds;
            idleExecutorGroup = new DefaultEventExecutorGroup(DEFAULT_IDLESTATEHANDLER_THREAD);
            beatsHandlerExecutorGroup = new DefaultEventExecutorGroup(beatsHandlerThread);
            tracker = new BatchTracker();
        }

        public void initChannel(SocketChannel socket) throws IOException, NoSuchAlgorithmException, CertificateException {
            ChannelPipeline pipeline = socket.pipeline();
            pipeline.addFirst(new TooManyBatchesFilter(tracker, maxInflightBatches));

            if(enableSSL) {
                SslHandler sslHandler = sslBuilder.build(socket.alloc());
                pipeline.addLast(SSL_HANDLER, sslHandler);
            }
            pipeline.addLast(idleExecutorGroup, IDLESTATE_HANDLER, new IdleStateHandler(clientInactivityTimeoutSeconds, IDLESTATE_WRITER_IDLE_TIME_SECONDS , clientInactivityTimeoutSeconds));
            pipeline.addLast(BEATS_ACKER, new AckEncoder());
            pipeline.addLast(CONNECTION_HANDLER, new ConnectionHandler());
            pipeline.addLast("BeatsParser", new BeatsParser(tracker));
            pipeline.addLast(beatsHandlerExecutorGroup, new BeatsHandler(this.message, tracker));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn("Exception caught in channel initializer", cause);
            try {
                this.message.onChannelInitializeException(ctx, cause);
            } finally {
                super.exceptionCaught(ctx, cause);
            }
        }

        public void shutdownEventExecutor() {
            try {
                idleExecutorGroup.shutdownGracefully().sync();
                beatsHandlerExecutorGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}

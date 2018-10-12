package com.myNetty.server;

import com.myNetty.handler.TextWebSocketFrameHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.RejectedExecutionHandlers;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 通道初始化类
 *
 * @author sjl
 */
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    /**
     * 业务线程池线程数
     */
    private static int eventExecutorGroupThreads = 0;

    /**
     * 业务线程池队列长度
     */
    private static int eventExecutorGroupQueues = 0;

    static {
        eventExecutorGroupThreads = Integer.getInteger("websocket.executor.threads", 0);
        if(eventExecutorGroupThreads == 0) {
            eventExecutorGroupThreads = Runtime.getRuntime().availableProcessors();
        }

        eventExecutorGroupQueues = Integer.getInteger("websocket.executor.queues", 0);
        if(eventExecutorGroupQueues == 0) {
            eventExecutorGroupQueues = 512;
        }
    }

    /**
     * 业务线程组
     */
    private static final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(
            eventExecutorGroupThreads, new ThreadFactory() {
        private AtomicInteger threadIndex = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "WebSocketRequestHandlerThread_" + this.threadIndex.incrementAndGet());
        }
    }, eventExecutorGroupQueues, RejectedExecutionHandlers.reject());

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        // WebSocket协议本身是基于HTTP协议的，所以要使用HTTP解编码器
        pipeline.addLast("httpServerCodec", new HttpServerCodec());
        // Netty是基于分段请求的，HttpObjectAggregator的作用是将请求分段再聚合,参数是聚合字节的最大长度
        pipeline.addLast("httpObjectAggregator", new HttpObjectAggregator(1024 * 64));
        // 已块的方式处理大块数据 防止撑爆内存
        pipeline.addLast("chunkedWriteHandler", new ChunkedWriteHandler());
        // 控制webSocket 连接路径
        pipeline.addLast("nameSpace", new WebSocketServerProtocolHandler("", true));
        // 消息处理器
        pipeline.addLast(eventExecutorGroup, new TextWebSocketFrameHandler());
    }
}

package com.myNetty;

import com.myNetty.server.NettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动类
 */
public class Starter {
    private static final Logger logger = LoggerFactory.getLogger(Starter.class);
    // 执行server的Executor
    private static final ExecutorService SOCKET_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        // todo 加载配置文件
        NettyServer nettyServer = new NettyServer(8099);
        SOCKET_EXECUTOR.execute(() -> {
            try {
                nettyServer.start();
            } catch (Throwable e) {
                logger.error("start webSocket Server failed, reason {}", e.getMessage());
            }
        });

    }
}

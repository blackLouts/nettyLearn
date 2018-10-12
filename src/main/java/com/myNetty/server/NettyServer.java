package com.myNetty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import com.myNetty.manage.ServerChannelsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * netty服务的启动入口
 */
public class NettyServer  implements InitializingBean, BeanDefinitionRegistryPostProcessor, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    /**
     * 执行server的Executor
     */
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    // serverChannel的执行线程组，线程数量为1
    private NioEventLoopGroup boss = new NioEventLoopGroup(1);
    // clientChannel的执行线程组，线程数量使用默认值
    private NioEventLoopGroup worker = new NioEventLoopGroup();

    /**
     * 监听端口号
     */
    private int port;

    private void start() {
        executorService.execute(() -> {
            try {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(boss, worker)
                        .channel(NioServerSocketChannel.class)
                        /*
                         * ChannelOption.SO_BACKLOG对应的是TCP/IP协议listen函数中的backlog参数，
                         * 函数listen(int socketfd,int backlog)用来初始化服务端可连接队列，服务端处理客户端连接请求是顺序处理的，所以同一时间只能处理一个客户端连接，
                         * 多个客户端来的时候，服务端将不能处理的客户端连接请求放在队列中等待处理，backlog参数指定了队列的大小
                         */
                        .option(ChannelOption.SO_BACKLOG, 128)
                        // 使用默认的 对象池
                        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .handler(new LoggingHandler(LogLevel.DEBUG))
                        // 禁用Nagle算法 降低TCP消息延迟
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        // 连接保持 KEEP-ALIVE 状态
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        // 使用默认的 对象池
                        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        // 自定义handler 处理连接
                        .childHandler(new WebSocketChannelInitializer());

                ChannelFuture future = serverBootstrap.bind(port).syncUninterruptibly();
                logger.info("The netty websocket com.myNetty.server is now ready to accept requests on port {}", NettyServer.this.port);
                // 初始化群组
                ServerChannelsHolder.getChannelGroups().put("all", new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE));
                // 阻塞关闭
                future.channel().closeFuture().syncUninterruptibly();
            } catch (Exception e) {
                logger.error("The netty websocket com.myNetty.server start error on {}", e.getMessage());
            } finally {
                // 释放资源
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        });
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }

    public void afterPropertiesSet() throws Exception {

    }

    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        start();
    }

    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    /**
     * 当容器关闭时 强制结束所有netty server相关的进程
     */
    public void destroy() throws Exception {
        try {
            if (!boss.isShutdown()) {
                boss.shutdownGracefully();
            }
            if (!worker.isShutdown()) {
                worker.shutdownGracefully();
            }
            executorService.shutdown();
            if (executorService.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        }catch (Exception e) {
            logger.info("Forced end of the netty com.myNetty.server.");
            logger.error(e.getMessage());
        }
    }
}

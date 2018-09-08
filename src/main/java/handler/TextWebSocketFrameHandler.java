package handler;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import manage.ServerChannelsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.JsonUtil;


/**
 * Http 处理器
 *
 * @author sjl
 */
public class TextWebSocketFrameHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handShaker;

    private static final Logger logger = LoggerFactory.getLogger(TextWebSocketFrameHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            // 处理HTTP请求
            handleHttpRequest(ctx, (FullHttpRequest) msg);
            return;
        }
        if (msg instanceof WebSocketFrame) {
            // 处理WebSocket请求
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error(cause.getMessage());
        ctx.close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ServerChannelsHolder.removeChannel(ctx.channel().id().asLongText());
        super.channelInactive(ctx);
    }

    /**
     * 处理HTTP请求
     *
     * @param ctx
     * @param request
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        // WebSocket访问，处理握手升级。
        if (HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(request.headers().get(HttpHeaderValues.UPGRADE))) {
            // Handshake
            WebSocketServerHandshakerFactory wsFactory =
                    new WebSocketServerHandshakerFactory("", null, true);
            handShaker = wsFactory.newHandshaker(request);
            if (handShaker == null) {
                // 无法处理的WebSocket版本
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                return;
            }

            // 向客户端发送WebSocket握手，完成握手。
            ChannelFuture channelFuture = handShaker.handshake(ctx.channel(), request);
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    future.channel().close();
                }
                // 加入到ChannelHolders中
            });
        } else {
            // 普通的HTTP访问
            logger.warn("无效的访问: {}", request.uri());
            // 处理 100 Continue 以符合规范
            if ( HttpUtil.is100ContinueExpected(request)) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
                ctx.writeAndFlush(response);
            }
            HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            // 请求是否keep-alive
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.write(response);
            ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!keepAlive) {
                // 没有请求keep-alive 则写操作完成后关闭channel
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    /**
     * 处理WebSocket请求
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handShaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            ctx.close();
            return;
        }
        // 没有使用WebSocketServerProtocolHandler，就不会接收到PingWebSocketFrame。
        if (frame instanceof PingWebSocketFrame) {
            logger.info(String.format("get Ping Frame From %s", frame.content().toString()));
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported", frame.getClass().getName()));
        }

        String request = ((TextWebSocketFrame) frame).text();
        logger.debug("收到客户端发送的数据：" + request);
        // 回复心跳
        if (request.length() == 0) {
            ctx.writeAndFlush(new TextWebSocketFrame(""));
            return;
        }
        this.handleMessage(ctx.channel(), request);
    }

    /**
     * 处理消息
     */
    private void handleMessage(Channel channel, String message) {
        JSONObject json = JsonUtil.checkJson(message);
        if (json == null) {
            logger.error("Incorrect message format.");
            return;
        }
        if(!json.containsKey("action")) {
            logger.warn("can not find action.");
            return;
        }
        switch(json.getString("action").toUpperCase()) {
            case "INIT_ACTION":
                bindChannelToUser(json, channel);
                break;
            case "BIND_CHANNEL_TO_GROUP":
                bindChannelToChannelGroup(json, channel);
                break;
            case "REMOVE_CHANNEL_FROM_GROUP":
                removeChannelFromChannelGroup(json, channel);
                break;
            case "SEND_MESSAGE_FOR_CLIENT":
                sendMessageForClient(json);
                break;
        }
    }

    /**
     * 绑定channel到ChannelGroup中
     */
    private void bindChannelToUser(JSONObject message, Channel channel) {
        String userId = message.getString("userId");
        ServerChannelsHolder.addChannel(userId, channel);
        JSONObject data = new JSONObject();
        data.put("users", ServerChannelsHolder.getUserSet());
        ServerChannelsHolder.sendMessage("all", data.toJSONString());
    }

    /**
     * 绑定channel到ChannelGroup中
     */
    private void bindChannelToChannelGroup(JSONObject message, Channel channel) {
        String groupId = message.getString("groupId");
        ServerChannelsHolder.addChannelToGroup(groupId, channel);
    }

    /**
     * 从GroupChannel中删除channel
     */
    private void removeChannelFromChannelGroup(JSONObject message, Channel channel) {
        String groupId = message.getString("groupId");
        ChannelGroup cg = ServerChannelsHolder.getChannelGroups().get(groupId);
        if(cg != null) {
            cg.remove(channel);
        }
    }

    /**
     * 转发推送 消息至用户端
     */
    private void sendMessageForClient(JSONObject message) {
        String groupId = message.getString("groupId");
        JSONObject msg = message.getJSONObject("message");
        ServerChannelsHolder.sendMessage(groupId, msg.toString());
    }
}

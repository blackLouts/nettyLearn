package manage;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 通道持有者类
 *
 * @author sjl
 */
@Component
public class ServerChannelsHolder {
    private static final Logger logger = LoggerFactory.getLogger(ServerChannelsHolder.class);
    /**
     * 存储所有channel
     */
    private static final Map<String, Channel> CHANNELS = new ConcurrentHashMap<>(1024);
    /**
     * ChannelGroup
     * group会自动监测里面的channel，当channel断开时，会主动踢出该channel，永远保留当前可用的channel列表 。
     */
    private static final Map<String, ChannelGroup> CHANNEL_GROUPS = new ConcurrentHashMap<>(100);

    /**
     * 存储 用户id 与channel Id 之间的映射
     */
    private static final Map<String, HashSet<String>> USER_MAP = new ConcurrentHashMap<>(100);

    /**
     * 存储 channel Id 与 用户id 之间的映射
     */
    private static final Map<String, String> CHANNEL_USER_MAP = new ConcurrentHashMap<>(100);
    /**
     * 读写锁
     */
    private static ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private static ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private static ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public static Map<String, ChannelGroup> getChannelGroups() {
        return CHANNEL_GROUPS;
    }

    public static Map<String, Channel> getChannels() {
        return CHANNELS;
    }

    public static Set<String> getUserSet() {
        return USER_MAP.keySet();
    }

    /**
     * 将用户对应的channel添加到对应的集合
     * @param userId
     * @param channel
     */
    public static void addChannel(String userId , Channel channel) {
        try {
            writeLock.lock();
            String channelId = channel.id().asLongText();
            CHANNELS.putIfAbsent(channelId, channel);
            CHANNEL_USER_MAP.putIfAbsent(channelId, userId);

            HashSet<String> channelIds = new HashSet<>();
            HashSet<String> returnChannelIds = USER_MAP.putIfAbsent(userId, channelIds);
            if (returnChannelIds == null) {
                channelIds.add(channelId);
            } else {
                returnChannelIds.add(channelId);
            }
            addChannelToGroup("all", channel);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }finally {
            writeLock.unlock();
        }
    }

    /**
     * 删除对应的Channel
     */
    public static void removeChannel(String channelId) {
        try {
            writeLock.lock();
            CHANNELS.remove(channelId);
            String userId = CHANNEL_USER_MAP.remove(channelId);
            if (userId != null) {
                HashSet<String> channels = USER_MAP.get(userId);
                if (channels != null) {
                    if (channels.remove(channelId)) {
                        if (channels.size() == 0) {
                            USER_MAP.remove(userId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }finally {
            writeLock.unlock();
        }
    }

    /**
     * 根据 用户Id 查询用户所有的channel
     * @param userId
     * @return
     */
    public static List<Channel> getChannelsByUserId(String userId) {
        try {
            readLock.lock();
            if (USER_MAP.containsKey(userId)) {
                List<Channel> channelList = new ArrayList<>();
                for (String channelId : USER_MAP.get(userId)) {
                    if (CHANNELS.containsKey(channelId)) {
                        channelList.add(CHANNELS.get(channelId));
                    }
                }
                return channelList;
            }
        }catch (Exception e) {
            logger.error(e.getMessage());
        }finally {
            readLock.unlock();
        }
        return null;
    }

    /**
     * 根据channelID 查询 用户ID
     * @param channelId
     * @return
     */
    public static String getUserIdByChannelId(String channelId) {
        try {
            readLock.lock();
            return CHANNEL_USER_MAP.get(channelId);
        }catch (Exception e) {
            logger.error(e.getMessage());
        }finally {
            readLock.unlock();
        }
        return null;
    }

    /**
     * 将channel添加到GroupChannel中
     * @param groupId
     * @param channel
     */
    public static void addChannelToGroup(String groupId, Channel channel) {
        DefaultChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        ChannelGroup returnChannelGroup = CHANNEL_GROUPS.putIfAbsent(groupId, channelGroup);
        if(returnChannelGroup == null) {
            // 不存在该ChannelGroup，第一次添加。
            channelGroup.add(channel);
            return;
        }
        // ChannelGroup已经存在
        returnChannelGroup.add(channel);
    }

    /**
     * 将消息 推送给指定组
     * @param groupId
     * @param message
     */
    public static void sendMessage(String groupId, String message) {
        if (CHANNEL_GROUPS.containsKey(groupId)) {
            CHANNEL_GROUPS.get(groupId).writeAndFlush(new TextWebSocketFrame(message));
        }
    }
}

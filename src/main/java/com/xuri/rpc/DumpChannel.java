package com.xuri.rpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 本地序列化通道，对应TS版本的DumpChannel。
 * 用于测试场景，在同一JVM内通过序列化/反序列化模拟网络传输。
 * 线程安全：消息处理使用synchronized确保顺序。
 */
public class DumpChannel {
    private volatile MessageReceiver serverSideReceiver;
    private volatile MessageReceiver clientSideReceiver;
    private volatile Client serverSideClient;
    private volatile Client clientSideClient;
    private final Gson gson;

    public DumpChannel() {
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    public void setServerSide(MessageReceiver messageReceiver, Client client) {
        this.serverSideReceiver = messageReceiver;
        this.serverSideClient = client;
    }

    public void setClientSide(MessageReceiver messageReceiver, Client client) {
        this.clientSideReceiver = messageReceiver;
        this.clientSideClient = client;
    }

    /**
     * 发送消息到服务端。
     * 通过Gson序列化/反序列化模拟真实网络传输。
     * 注意：不使用synchronized，因为服务端处理请求时需要发送响应回客户端，
     * 如果同一个线程持有锁会导致死锁。线程安全由底层RPC机制保证。
     */
    public void sendToServer(Object message) throws Exception {
        // 序列化再反序列化，模拟网络传输
        String json = gson.toJson(message);
        Object deserialized = gson.fromJson(json, Object.class);
        serverSideReceiver.onReceiveMessage(deserialized, serverSideClient);
    }

    /**
     * 发送消息到客户端。
     */
    public void sendToClient(Object message) throws Exception {
        String json = gson.toJson(message);
        Object deserialized = gson.fromJson(json, Object.class);
        clientSideReceiver.onReceiveMessage(deserialized, clientSideClient);
    }

    /**
     * 创建服务端。
     * 对应TS版本的createServer。
     */
    public ServerFactory createServer(final String hostId) {
        final MessageReceiver messageReceiver = new MessageReceiver(hostId);
        final Client client = new Client(hostId);
        final DumpChannel channel = this;

        client.setSender(new ISender() {
            public void send(Object message) throws Exception {
                channel.sendToClient(message);
            }
        });

        channel.setServerSide(messageReceiver, client);

        return new ServerFactory(messageReceiver);
    }

    /**
     * 创建客户端。
     * 对应TS版本的createMain。
     */
    public ClientResult createMain(final String hostId) {
        final Client client = new Client(hostId);
        final MessageReceiver messageReceiver = new MessageReceiver(hostId);
        final DumpChannel channel = this;

        channel.setClientSide(messageReceiver, client);

        client.setSender(new ISender() {
            public void send(Object message) throws Exception {
                channel.sendToServer(message);
            }
        });

        return new ClientResult(client, messageReceiver);
    }

    public static class ServerFactory {
        private final MessageReceiver receiver;

        ServerFactory(MessageReceiver receiver) {
            this.receiver = receiver;
        }

        public ServerResult serve(Object mainObject) {
            receiver.setMain(mainObject);
            return new ServerResult(receiver, mainObject);
        }
    }

    public static class ServerResult {
        public final MessageReceiver receiver;
        public final Object mainObject;

        ServerResult(MessageReceiver receiver, Object mainObject) {
            this.receiver = receiver;
            this.mainObject = mainObject;
        }
    }

    public static class ClientResult {
        public final Client client;
        public final MessageReceiver receiver;

        ClientResult(Client client, MessageReceiver receiver) {
            this.client = client;
            this.receiver = receiver;
        }
    }
}

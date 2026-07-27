package com.xuri.rpc;

/**
 * 消息发送接口，对应TS版本的ISender。
 */
public interface ISender {
    void send(Object message) throws Exception;
}

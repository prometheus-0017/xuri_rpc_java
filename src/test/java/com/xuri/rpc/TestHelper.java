package com.xuri.rpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试辅助类，对应TS版本的__tests__/base.ts。
 * 提供创建测试用的服务端和客户端的便捷方法。
 */
public class TestHelper {
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    /**
     * 测试流程接口。
     */
    public interface TestProcess {
        void run(Client client, Object main, String serverId) throws Exception;
    }

    /**
     * 创建一个完整的测试环境（服务端+客户端），执行测试流程。
     */
    public static void mainFunc(Object mainObject, TestProcess testProcess) throws Exception {
        mainFunc(mainObject, testProcess, null, null);
    }

    public static void mainFunc(Object mainObject, TestProcess testProcess, String serverId, String clientId) throws Exception {
        int id = idCounter.getAndIncrement();
        if (serverId == null) serverId = "server" + id;
        if (clientId == null) clientId = "client" + id;

        RpcFramework.reset();

        DumpChannel channel = new DumpChannel();
        DumpChannel.ServerFactory server = channel.createServer(serverId);
        server.serve(mainObject);
        DumpChannel.ClientResult clientResult = channel.createMain(clientId);

        Client client = clientResult.client;
        Object main = client.getMain().get(5, TimeUnit.SECONDS);
        testProcess.run(client, main, serverId);
    }

    /**
     * 断言辅助。
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message != null ? message : "Assertion failed");
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError((message != null ? message + ": " : "") +
                "expected <" + expected + "> but got <" + actual + ">");
    }
}

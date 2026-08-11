package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 异常处理测试，对应TS版本的exception.test.ts和error_handling.test.ts。
 * 主对象是真正的Java对象，服务端方法抛出的异常应传播回客户端。
 */
class ExceptionTest {

    public static class FailingService {
        public int add(int a, int b) {
            throw new RuntimeException("testException");
        }
    }

    @Test
    void testServerExceptionPropagation() throws Exception {
        TestHelper.mainFunc(new FailingService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                boolean caught = false;
                try {
                    proxy.invoke("add", 1, 2);
                } catch (RpcException e) {
                    caught = true;
                    assertEquals(-1, e.getStatus());
                    assertTrue(e.getTrace().contains("testException"));
                }
                assertTrue(caught, "exception should have been caught");
            }
        });
    }

    public static class HelloService {
        public String hello() {
            return "world";
        }
    }

    @Test
    void testObjectNotFoundViaDumpChannel() throws Exception {
        RpcFramework.reset();
        String serverHostId = "errNotFoundServer";
        String clientHostId = "errNotFoundClient";

        DumpChannel channel = new DumpChannel();
        DumpChannel.ServerFactory server = channel.createServer(serverHostId);
        server.serve(new HelloService());
        DumpChannel.ClientResult clientResult = channel.createMain(clientHostId);
        Client client = clientResult.client;

        // 发送请求到不存在的objectId
        boolean errorCaught = false;
        try {
            client.waitForRequest(new Request(
                    "testReq1", new HashMap<String, Object>(),
                    "someMethod", "nonExistentObj", new ArrayList<Object>()
            )).get();
        } catch (Exception e) {
            errorCaught = true;
            Throwable cause = e.getCause();
            if (cause instanceof RpcException) {
                RpcException rpcEx = (RpcException) cause;
                assertEquals(100, rpcEx.getStatus());
                assertEquals("object not found", rpcEx.getTrace());
            }
        }
        assertTrue(errorCaught, "error should have been caught");
    }

    @Test
    void testGenerateErrorReply() {
        Request request = new Request("req123", new HashMap<String, Object>(), "testMethod", "obj1", new ArrayList<Object>());
        Response reply = RpcFramework.generateErrorReply(request, "something went wrong", 500, null);
        assertEquals("req123", reply.getIdFor());
        assertEquals(500, reply.getStatus());
        assertEquals("something went wrong", reply.getTrace());
        assertNotNull(reply.getMeta());
    }
}

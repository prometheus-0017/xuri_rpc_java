package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 异常处理测试，对应TS版本的exception.test.ts和error_handling.test.ts。
 */
class ExceptionTest {

    @Test
    void testServerExceptionPropagation() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("add", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                throw new RuntimeException("testException");
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
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

    @Test
    void testObjectNotFoundViaDumpChannel() throws Exception {
        RpcFramework.reset();
        String serverHostId = "errNotFoundServer";
        String clientHostId = "errNotFoundClient";

        DumpChannel channel = new DumpChannel();
        DumpChannel.ServerFactory server = channel.createServer(serverHostId);
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("hello", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) { return "world"; }
        }));
        server.serve(mainObj);
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

package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Null处理测试，对应TS版本的null.test.ts和null_in_dict_test.ts。
 */
class NullTest {

    @Test
    void testNullArgument() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("add", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                assertNull(args[2], "callback should be null");
                return ((Number) args[0]).intValue() + ((Number) args[1]).intValue();
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Object result = proxy.invoke("add", 1, 2, null);
                assertEquals(3, ((Number) result).intValue());
            }
        });
    }

    @Test
    void testNullInDict() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("checkNull", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pack = (Map<String, Object>) args[0];
                assertNull(pack.get("b"), "b should be null");
                assertNull(pack.get("c"), "c should be null");
                return ((Number) pack.get("a")).intValue() + 1;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Map<String, Object> pack = new LinkedHashMap<String, Object>();
                pack.put("a", 1);
                pack.put("b", null);
                pack.put("c", null);
                Object result = proxy.invoke("checkNull", pack);
                assertEquals(2, ((Number) result).intValue());
            }
        });
    }

    @Test
    void testMixedNullInDict() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("mixedNull", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pack = (Map<String, Object>) args[0];
                assertEquals(10, ((Number) pack.get("x")).intValue());
                assertNull(pack.get("y"));
                assertEquals(30, ((Number) pack.get("z")).intValue());
                return ((Number) pack.get("x")).intValue() + ((Number) pack.get("z")).intValue();
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Map<String, Object> pack = new LinkedHashMap<String, Object>();
                pack.put("x", 10);
                pack.put("y", null);
                pack.put("z", 30);
                Object result = proxy.invoke("mixedNull", pack);
                assertEquals(40, ((Number) result).intValue());
            }
        });
    }
}

package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 返回类型测试，对应TS版本的return_types.test.ts。
 */
class ReturnTypesTest {

    @Test
    void testReturnString() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("greet", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                return "hello " + args[0];
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertEquals("hello world", proxy.invoke("greet", "world"));
            }
        });
    }

    @Test
    void testReturnBoolean() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("isPositive", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                return ((Number) args[0]).intValue() > 0;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertEquals(true, proxy.invoke("isPositive", 5));
                assertEquals(false, proxy.invoke("isPositive", -1));
            }
        });
    }

    @Test
    void testReturnPlainDict() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("getData", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                result.put("a", 1);
                result.put("b", "hello");
                result.put("c", true);
                return result;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) proxy.invoke("getData");
                assertEquals(1, ((Number) result.get("a")).intValue());
                assertEquals("hello", result.get("b"));
                assertEquals(true, result.get("c"));
            }
        });
    }

    @Test
    void testReturnNull() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("doNothing", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                return null;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertNull(proxy.invoke("doNothing"));
            }
        });
    }

    @Test
    void testReturnNumber() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("double", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                return ((Number) args[0]).intValue() * 2;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertEquals(14, ((Number) proxy.invoke("double", 7)).intValue());
            }
        });
    }

    @Test
    void testReturnNestedDict() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("getNested", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                Map<String, Object> inner = new LinkedHashMap<String, Object>();
                inner.put("inner", 42);
                Map<String, Object> outer = new LinkedHashMap<String, Object>();
                outer.put("outer", inner);
                return outer;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) proxy.invoke("getNested");
                @SuppressWarnings("unchecked")
                Map<String, Object> inner = (Map<String, Object>) result.get("outer");
                assertEquals(42, ((Number) inner.get("inner")).intValue());
            }
        });
    }

    @Test
    void testReturnListOfPrimitives() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("getList", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                List<Object> list = new ArrayList<Object>();
                list.add(1);
                list.add("two");
                list.add(true);
                return list;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                @SuppressWarnings("unchecked")
                List<Object> result = (List<Object>) proxy.invoke("getList");
                assertEquals(1, ((Number) result.get(0)).intValue());
                assertEquals("two", result.get(1));
                assertEquals(true, result.get(2));
            }
        });
    }
}

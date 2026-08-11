package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 返回类型测试，对应TS版本的return_types.test.ts。
 * 主对象是一个真正的Java对象，各方法返回不同类型的值。
 * 注意：double是Java关键字，因此翻倍方法命名为doubleIt。
 */
class ReturnTypesTest {

    public static class MainService {
        public String greet(String name) {
            return "hello " + name;
        }

        public boolean isPositive(int value) {
            return value > 0;
        }

        public Map<String, Object> getData() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("a", 1);
            result.put("b", "hello");
            result.put("c", true);
            return result;
        }

        public Object doNothing() {
            return null;
        }

        public int doubleIt(int value) {
            return value * 2;
        }

        public Map<String, Object> getNested() {
            Map<String, Object> inner = new LinkedHashMap<String, Object>();
            inner.put("inner", 42);
            Map<String, Object> outer = new LinkedHashMap<String, Object>();
            outer.put("outer", inner);
            return outer;
        }

        public List<Object> getList() {
            List<Object> list = new ArrayList<Object>();
            list.add(1);
            list.add("two");
            list.add(true);
            return list;
        }
    }

    @Test
    void testReturnString() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertEquals("hello world", proxy.invoke("greet", "world"));
            }
        });
    }

    @Test
    void testReturnBoolean() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertEquals(true, proxy.invoke("isPositive", 5));
                assertEquals(false, proxy.invoke("isPositive", -1));
            }
        });
    }

    @Test
    void testReturnPlainDict() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
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
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertNull(proxy.invoke("doNothing"));
            }
        });
    }

    @Test
    void testReturnNumber() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                assertEquals(14, ((Number) proxy.invoke("doubleIt", 7)).intValue());
            }
        });
    }

    @Test
    void testReturnNestedDict() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
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
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
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

package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 对象代理测试，测试对象在字典/数组中的代理行为。
 * 对应TS版本的obj_in_dict.test.ts, obj_in_array.test.ts, obj_in_dict_in_array.test.ts。
 *
 * 在Java中，非Map/非基本类型的对象会被自动转为代理。
 * NumberObject是一个有方法的类，所以它会被当作"对象"而非"字典"。
 */
class ObjectProxyTest {

    /**
     * 模拟TS版本中的NumberObject类。
     * 有方法（increase, getValue），所以是"对象"而非"字典"。
     */
    static class NumberObject {
        private int value;

        NumberObject(int value) {
            this.value = value;
        }

        public void increase() {
            value++;
        }

        public int getValue() {
            return value;
        }
    }

    public static class DictAddService {
        public int add(Map<String, Object> pack) throws Exception {
            // pack中的a, b, c是远程代理对象
            RemoteProxyObject a = (RemoteProxyObject) pack.get("a");
            RemoteProxyObject b = (RemoteProxyObject) pack.get("b");
            RemoteProxyObject c = (RemoteProxyObject) pack.get("c");
            a.invoke("increase");
            c.invoke("increase");
            b.invoke("increase");
            int va = ((Number) a.invoke("getValue")).intValue();
            int vb = ((Number) b.invoke("getValue")).intValue();
            return va + vb;
        }
    }

    @Test
    void testObjectInDict() throws Exception {
        TestHelper.mainFunc(new DictAddService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                NumberObject a = new NumberObject(0);
                NumberObject b = new NumberObject(0);
                NumberObject c = a; // 同一个对象

                Map<String, Object> pack = new LinkedHashMap<String, Object>();
                pack.put("a", Client.asProxy(a, client.getHostId()));
                pack.put("b", Client.asProxy(b, client.getHostId()));
                pack.put("c", Client.asProxy(c, client.getHostId()));

                Object result = proxy.invoke("add", pack);
                assertEquals(3, ((Number) result).intValue()); // (0+1) + (0+1) = 2, 但a和c是同一对象所以a.increase和c.increase各加1
                // a.increase → value=1, c.increase → value=2 (同一对象), b.increase → value=1
                // a.getValue() = 2, b.getValue() = 1, 2+1=3
            }
        });
    }

    public static class ArrayAddService {
        public int add(List<Object> pack) throws Exception {
            RemoteProxyObject a = (RemoteProxyObject) pack.get(0);
            RemoteProxyObject b = (RemoteProxyObject) pack.get(1);
            RemoteProxyObject c = (RemoteProxyObject) pack.get(2);
            a.invoke("increase");
            c.invoke("increase");
            b.invoke("increase");
            int va = ((Number) a.invoke("getValue")).intValue();
            int vb = ((Number) b.invoke("getValue")).intValue();
            return va + vb;
        }
    }

    @Test
    void testObjectInArray() throws Exception {
        TestHelper.mainFunc(new ArrayAddService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                NumberObject a = new NumberObject(0);
                NumberObject b = new NumberObject(0);

                List<Object> pack = new ArrayList<Object>();
                pack.add(Client.asProxy(a, client.getHostId()));
                pack.add(Client.asProxy(b, client.getHostId()));
                pack.add(Client.asProxy(a, client.getHostId())); // 同一对象

                Object result = proxy.invoke("add", pack);
                assertEquals(3, ((Number) result).intValue());
            }
        });
    }

    public static class ItemProcessService {
        public int process(List<Object> items) throws Exception {
            for (Object item : items) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = (Map<String, Object>) item;
                RemoteProxyObject obj = (RemoteProxyObject) entry.get("obj");
                obj.invoke("increase");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) items.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> second = (Map<String, Object>) items.get(1);
            RemoteProxyObject obj0 = (RemoteProxyObject) first.get("obj");
            RemoteProxyObject obj1 = (RemoteProxyObject) second.get("obj");
            int v0 = ((Number) obj0.invoke("getValue")).intValue();
            int v1 = ((Number) obj1.invoke("getValue")).intValue();
            return v0 + v1;
        }
    }

    @Test
    void testObjectInDictInArray() throws Exception {
        TestHelper.mainFunc(new ItemProcessService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                NumberObject a = new NumberObject(1);
                NumberObject b = new NumberObject(2);

                List<Object> items = new ArrayList<Object>();
                Map<String, Object> entry1 = new LinkedHashMap<String, Object>();
                entry1.put("obj", Client.asProxy(a, client.getHostId()));
                Map<String, Object> entry2 = new LinkedHashMap<String, Object>();
                entry2.put("obj", Client.asProxy(b, client.getHostId()));
                items.add(entry1);
                items.add(entry2);

                Object result = proxy.invoke("process", items);
                // a: 1+1=2, b: 2+1=3, 2+3=5
                assertEquals(5, ((Number) result).intValue());
            }
        });
    }
}

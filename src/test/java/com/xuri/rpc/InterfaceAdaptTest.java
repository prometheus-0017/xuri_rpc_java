package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口适配测试：验证普通Java对象无需任何改动即可接收远程对象。
 * 服务端方法把参数声明为普通接口，框架会为远程代理生成实现该接口的动态代理。
 */
class InterfaceAdaptTest {

    /** 普通业务接口，服务端只依赖它，不感知RPC。 */
    public interface Counter {
        void increase();

        int getValue();
    }

    /** 函数式接口，用于接收回调。 */
    public interface ResultHandler {
        void handle(int value);
    }

    /** 完全普通的服务实现：参数是接口，没有任何RPC相关代码。 */
    public static class PlainService {
        public int sumOf(Counter a, Counter b) {
            a.increase();
            b.increase();
            return a.getValue() + b.getValue();
        }

        public int addAndNotify(int a, int b, ResultHandler handler) {
            handler.handle(a + b);
            return a + b;
        }

        public String describe(Counter counter) {
            return counter.toString();
        }
    }

    /** 客户端的普通对象，同样没有实现任何RPC接口。 */
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

    @Test
    void testRemoteObjectAdaptedToInterface() throws Exception {
        TestHelper.mainFunc(new PlainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                NumberObject a = new NumberObject(1);
                NumberObject b = new NumberObject(10);

                Object result = proxy.invoke("sumOf",
                        Client.asProxy(a, client.getHostId()),
                        Client.asProxy(b, client.getHostId()));

                // 服务端通过Counter接口调用，实际作用在客户端的NumberObject上
                assertEquals(13, ((Number) result).intValue());
                assertEquals(2, a.getValue());
                assertEquals(11, b.getValue());
            }
        });
    }

    @Test
    void testRemoteCallableAdaptedToFunctionalInterface() throws Exception {
        TestHelper.mainFunc(new PlainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                final AtomicInteger received = new AtomicInteger(0);

                CallableObject callback = new CallableObject(new CallableObject.RpcFunction() {
                    public Object call(Object... args) {
                        received.set(((Number) args[0]).intValue());
                        return null;
                    }
                });

                Object result = proxy.invoke("addAndNotify", 2, 3,
                        Client.asProxy(callback, client.getHostId()));

                // 只有__call__成员的远程代理，其唯一抽象方法handle被映射到__call__
                assertEquals(5, ((Number) result).intValue());
                assertEquals(5, received.get());
            }
        });
    }

    @Test
    void testAdaptedProxyToStringDoesNotGoRemote() throws Exception {
        TestHelper.mainFunc(new PlainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                NumberObject a = new NumberObject(0);
                Object described = proxy.invoke("describe", Client.asProxy(a, client.getHostId()));
                // Object的方法在本地处理，不会转发为远程调用
                assertTrue(((String) described).startsWith("RemoteProxy["), "got: " + described);
            }
        });
    }

    @Test
    void testAdaptRejectsConcreteClass() {
        RemoteProxyObject remote = new RemoteProxyObject("someId", new Client("adaptRejectHost"));
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            public void execute() {
                RemoteObjectAdapter.asInterface(remote, NumberObject.class);
            }
        });
    }
}

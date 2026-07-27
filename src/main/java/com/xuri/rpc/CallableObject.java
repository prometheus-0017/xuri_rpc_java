package com.xuri.rpc;

/**
 * 可调用对象包装器，对应TS版本中被asProxy包装的函数。
 * 在Java中函数不是一等对象，需要用此包装。
 * 当asProxy遇到此类型时，会生成一个带有__call__成员的代理描述。
 */
public class CallableObject {
    private final RpcFunction function;

    public CallableObject(RpcFunction function) {
        this.function = function;
    }

    public RpcFunction getFunction() {
        return function;
    }

    /**
     * 通用函数式接口，接受可变参数并返回Object。
     */
    public interface RpcFunction {
        Object call(Object... args) throws Exception;
    }
}

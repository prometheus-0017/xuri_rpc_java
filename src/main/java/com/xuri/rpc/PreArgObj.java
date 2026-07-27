package com.xuri.rpc;

/**
 * 对应TS版本的PreArgObj，用于在序列化前包装对象。
 * type为PROXY表示这是一个代理对象，type为DATA表示纯数据。
 */
public class PreArgObj {
    private ArgObjType type;
    private Object data;

    public PreArgObj(ArgObjType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public ArgObjType getType() {
        return type;
    }

    public void setType(ArgObjType type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

package com.xuri.rpc;

/**
 * 表示ArgObj的类型标记。
 * "proxy" 表示远程代理对象，"data" 表示纯数据，null 表示空。
 */
public enum ArgObjType {
    PROXY("proxy"),
    DATA("data");

    private final String value;

    ArgObjType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ArgObjType fromString(String s) {
        if (s == null) return null;
        for (ArgObjType t : values()) {
            if (t.value.equals(s)) return t;
        }
        return null;
    }
}

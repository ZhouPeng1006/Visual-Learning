package com.zp.visuallearningservice.models;

/**
 * @author ZP
 * @date 2023/6/9 18:54
 * @description TODO
 */

public class Result {
    private String message;
    private Object data;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

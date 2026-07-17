package com.example.myspringai.test;

public class Test03 {
    public static void main(String[] args) {
        ThreadLocal<Integer> threadLocal = new ThreadLocal<>();

        new Thread(() -> {
            threadLocal.set(1);
        }).start();

        new Thread(() -> {
            threadLocal.set(2);
        }).start();


    }
}

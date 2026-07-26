package com.example.myspringai.test;

import java.util.Map;
import java.util.WeakHashMap;

public class Test04 {
   static Map map = new WeakHashMap<>();
    public static void main(String[] args) throws InterruptedException {
        ini();
        syso();
    }
    public static void ini() throws InterruptedException {

        String str = new String("aaa");
        map.put(str, "bbb");
        System.out.println(str);

    }

    public static void syso() throws InterruptedException {
        for (Object o : map.keySet()) {
            System.out.println(o);
        }
        System.gc();
        Thread.sleep(5000);
        for (Object o : map.keySet()) {
            System.out.println(o);
        }
    }
}

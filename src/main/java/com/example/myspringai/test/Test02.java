package com.example.myspringai.test;

import org.w3c.dom.ls.LSOutput;

import java.lang.ref.SoftReference;

public class Test02 {
    public static void main(String[] args) {
        SoftReference<byte[]> sr = new SoftReference<>(new byte[1024 * 1024 * 10]);
        System.out.println("第一次 get: " + sr.get()); // 应该打印数组地址

        System.gc();
        try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("GC 后 get: " + sr.get()); // 此时应该还是数组地址，因为内存充足

//        byte[] bytes = new byte[1024 * 1024 * 12];
        SoftReference<byte[]> sr2 = new SoftReference<>(new byte[1024 * 1024 * 12]);
        System.gc();
        try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("分配大对象后 get: " + sr.get()); // 此时很可能打印 null，因为 byte[] 被回收了
        System.out.println("分配大对象后 get: " + sr2.get()); // 此时很可能打印 null，因为 byte[] 被回收了
    }
}

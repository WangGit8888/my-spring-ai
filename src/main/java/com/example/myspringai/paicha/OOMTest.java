package com.example.myspringai.paicha;

import java.util.ArrayList;
import java.util.List;

public class OOMTest {
    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        int count = 0;
        try {
            while (true) {
                // 每次分配 1MB 数组
                list.add(new byte[1024 * 1024]);
                count++;
                System.out.println("已分配 " + count + " MB");
                // 稍微慢一点，便于观察
                Thread.sleep(10);
            }
        } catch (OutOfMemoryError e) {
            System.err.println("OOM 错误! 已分配 " + count + " MB");
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

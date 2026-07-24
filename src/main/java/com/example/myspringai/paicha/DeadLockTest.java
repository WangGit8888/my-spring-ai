package com.example.myspringai.paicha;

public class DeadLockTest {
    // 两把锁对象
    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) {
        // 线程1：先拿 LOCK_A，再拿 LOCK_B
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("线程1: 获取到 LOCK_A");
                try {
                    Thread.sleep(100); // 确保线程2有机会拿到 LOCK_B
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (LOCK_B) {
                    System.out.println("线程1: 获取到 LOCK_B");
                }
            }
        }, "线程1");

        // 线程2：先拿 LOCK_B，再拿 LOCK_A（顺序相反！）
        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                System.out.println("线程2: 获取到 LOCK_B");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (LOCK_A) {
                    System.out.println("线程2: 获取到 LOCK_A");
                }
            }
        }, "线程2");

        t1.start();
        t2.start();
    }
}

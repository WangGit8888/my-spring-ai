package com.example.myspringai.test;

public class Test06 {
    // 为了让外部类更显眼，加一个 finalize() 方法
    @Override
    protected void finalize() throws Throwable {
        System.out.println(">>> Test06 外部类对象被 GC 回收了！");
        super.finalize();
    }

    // 非静态内部类
    public class InClass {
        // 内部类也加一个 finalize()，看它是否被回收
        @Override
        protected void finalize() throws Throwable {
            System.out.println(">>> InClass 内部类对象被 GC 回收了！");
            super.finalize();
        }
    }
}

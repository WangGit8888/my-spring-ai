package com.example.myspringai.test;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

public class Test05 {
    public static void main(String[] args) throws InterruptedException {
        // 1. 创建外部类和内部类
        Test06 test06 = new Test06();
        Test06.InClass inClass = test06.new InClass();

        // 2. 把内部类对象放入 List
        List<Test06.InClass> list = new ArrayList<>();
        list.add(inClass);

        // 3. 打印初始状态
        System.out.println("=== 初始状态 ===");
        System.out.println("外部类对象地址: " + System.identityHashCode(test06));
        System.out.println("内部类对象地址: " + System.identityHashCode(inClass));
        System.out.println("List中的内部类地址: " + System.identityHashCode(list.get(0)));

        // 4. 断开外部类的强引用
        test06 = null;

        // 5. 建议 JVM 执行 GC
        System.out.println("\n=== 触发 GC ===");
        System.gc();
        Thread.sleep(2000);  // 等待 GC 执行

        // 6. 再次打印（验证内部类是否还活着）
        System.out.println("\n=== GC 之后 ===");
        System.out.println("内部类对象是否存活: " + (inClass != null));
        System.out.println("内部类对象地址: " + System.identityHashCode(inClass));
        System.out.println("List中的内部类地址: " + System.identityHashCode(list.get(0)));

        // 7. 关键验证：外部类是否被回收了？
        // 我们无法直接访问 test06（已经置 null），但可以通过内部类的隐式引用链来判断
        System.out.println("\n=== 验证外部类是否存活 ===");
        // 注意：如果内部类还活着，它内部的 this$0 强引用会让外部类也活着
        // 所以只要内部类还活着，外部类就一定没被回收！
        System.out.println("结论：因为内部类对象还存活，所以外部类对象【没有被回收】！");
    }
}

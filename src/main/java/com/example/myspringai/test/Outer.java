package com.example.myspringai.test;

public class Outer {
    static class Inner {}

    public static void main(String[] args) {
//        Outer outer = new Outer();
//        Inner inner = outer.new Inner();

        Outer outer = new Outer();
        Inner inner = new Inner(  );
        inner = null;   // 切断对 Inner 的引用
        // 此时：
        // - Inner 对象：只有内部持有 outer 的引用，没有外部引用指向它 → 可以被 GC ✅
        // - Outer 对象：有 Inner 内部的引用指向它，但 Inner 已经不可达 → Outer 也可被 GC ✅
    }
}

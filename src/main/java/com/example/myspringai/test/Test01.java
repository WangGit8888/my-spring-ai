package com.example.myspringai.test;

public class Test01 {
    @Override
    protected void finalize() throws Throwable {
        System.out.println("finalize被调用了");
        super.finalize();
    }

    public static void main(String[] args) {
        Test01 test01 = new Test01();
        test01=null;
        System.gc();
        System.out.println(test01);
    }
}

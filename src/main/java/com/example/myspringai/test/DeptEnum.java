package com.example.myspringai.test;

import jakarta.persistence.criteria.CriteriaBuilder;

public enum DeptEnum {
    DEPT1(1,"安全部"),
    DEPT2(2,"信息部");

    DeptEnum(int code, String name) {
    }
}

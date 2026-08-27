package com.example.myspringai.controller;

import com.example.myspringai.config.aop.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    @RequiresPermission("user:delete")
    @GetMapping("test01")
    public void test() {
        System.out.println("aaaa");
    }
}

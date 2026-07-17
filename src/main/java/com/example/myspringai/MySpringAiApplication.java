package com.example.myspringai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.myspringai.mapper")
public class MySpringAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MySpringAiApplication.class, args);
    }

}

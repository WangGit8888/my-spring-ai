package com.example.myspringai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.myspringai.mapper")
public class MySpringAiApplication {

    public static void main(String[] args) {
//        SpringApplication.run(MySpringAiApplication.class, args);
        // 启动应用
        ConfigurableApplicationContext context = SpringApplication.run(MySpringAiApplication.class, args);

        // 获取并打印 Tomcat 容器的信息
        ServletWebServerApplicationContext webContext = (ServletWebServerApplicationContext) context;
        WebServer webServer = webContext.getWebServer();
        System.out.println("当前运行的 Web 服务器端口: " + webServer.getPort());
        // 输出：当前运行的 Web 服务器端口: 8080
    }

}

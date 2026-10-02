package com.lingjiuw.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CMS 系统启动入口。
 * 单进程同时提供：REST API（/api/**）、后台管理界面（/，来自 classpath:static）、Swagger（/swagger-ui.html）。
 */
@SpringBootApplication
public class CmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsApplication.class, args);
    }
}

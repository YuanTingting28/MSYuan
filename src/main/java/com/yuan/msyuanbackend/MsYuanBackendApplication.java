package com.yuan.msyuanbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.yuan.msyuanbackend.mapper")
@EnableScheduling
@ServletComponentScan
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@EnableCaching
public class MsYuanBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsYuanBackendApplication.class, args);
    }

}

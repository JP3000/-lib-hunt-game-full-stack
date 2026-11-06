package com.libhunt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.libhunt.mapper")
public class LibHuntApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibHuntApplication.class, args);
    }
}

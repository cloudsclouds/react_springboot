package com.example.server_springboot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.server_springboot.**.mapper")
public class ServerSpringbootApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerSpringbootApplication.class, args);
	}

}

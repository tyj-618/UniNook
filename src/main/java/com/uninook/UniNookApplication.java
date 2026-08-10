package com.uninook;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.ai.AiProperties;
import com.uninook.ai.SearchProperties;
import com.uninook.auth.AuthProperties;
import com.uninook.user.AvatarStorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan(value = "com.uninook", markerInterface = BaseMapper.class)
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AiProperties.class, AuthProperties.class, SearchProperties.class, AvatarStorageProperties.class})
public class UniNookApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniNookApplication.class, args);
    }

}

package com.campuscircle;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuscircle.ai.AiProperties;
import com.campuscircle.ai.SearchProperties;
import com.campuscircle.auth.AuthProperties;
import com.campuscircle.user.AvatarStorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan(value = "com.campuscircle", markerInterface = BaseMapper.class)
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AiProperties.class, AuthProperties.class, SearchProperties.class, AvatarStorageProperties.class})
public class CampusCircleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusCircleApplication.class, args);
    }

}

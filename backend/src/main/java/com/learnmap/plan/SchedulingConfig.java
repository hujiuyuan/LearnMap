package com.learnmap.plan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 夜间重排等定时任务；测试里关掉 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "learnmap.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

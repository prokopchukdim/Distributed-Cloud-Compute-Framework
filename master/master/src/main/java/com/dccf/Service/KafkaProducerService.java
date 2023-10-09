package com.dccf.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Slf4j
@Service
public class KafkaProducerService {
    private KafkaTemplate<String, String> kafkaTemplate;
    private static final String JOB_TOPIC = "jobIdTopic";

    public void submitTask(String taskId) {
        log.info("Submitting task {}", taskId);
        kafkaTemplate.send(JOB_TOPIC, taskId);
    }
}

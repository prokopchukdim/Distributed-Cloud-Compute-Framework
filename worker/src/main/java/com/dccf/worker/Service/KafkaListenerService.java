package com.dccf.worker.Service;

import com.dccf.worker.Const.ConstantProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaListenerService {
    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private WorkerService workerService;
    @Autowired
    private static ConstantProvider constantProvider;

    @KafkaListener(id="listenerSingleton"/*constantProvider.LISTENER_ID*/, autoStartup = "true", topics = "jobIdTopic")
    public void consume(String message) {
        log.info("Received Message: {}", message);
        kafkaListenerEndpointRegistry.getListenerContainer(constantProvider.LISTENER_ID).stop(() -> {
            log.info("Listener paused for processing");
        });
        workerService.pushJob(message);
    }
}

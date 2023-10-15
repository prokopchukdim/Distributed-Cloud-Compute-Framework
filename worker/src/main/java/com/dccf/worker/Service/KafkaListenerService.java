package com.dccf.worker.Service;

import com.dccf.worker.Const.ConstantProvider;
import com.dccf.worker.Const.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class KafkaListenerService {
    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private static ConstantProvider constantProvider;

    @KafkaListener(id="listenerSingleton", autoStartup = "true", topics = "jobIdTopic")
    public void consume(String message) throws IOException {
        log.info("Received task id: {}", message);
        MessageListenerContainer listenerContainer = kafkaListenerEndpointRegistry.getListenerContainer(constantProvider.LISTENER_ID);

        if (listenerContainer.isPauseRequested()) {
            return;
        } else {
            log.info("Pausing listener for processing");
            listenerContainer.pause();
        }

        CompletableFuture.runAsync(() -> {
            try {
                workerService.pushJob(message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }



}

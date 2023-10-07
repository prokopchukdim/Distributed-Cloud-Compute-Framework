package com.dccf.worker.Service;

import com.dccf.worker.Const.ConstantProvider;
import com.dccf.worker.Const.JobStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;

public class SchedulerService {
    private static final int KAFKA_LISTENER_SCHEDULE = 1000;
    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired
    private ConstantProvider constantProvider;
    @Autowired
    WorkerService workerService;


    @Scheduled(fixedRate = KAFKA_LISTENER_SCHEDULE)
    public void kafkaListenerManager() {
        if (JobStatus.NO_JOB.equals(workerService.getCurrentStatus()) || JobStatus.EXITED.equals(workerService.getCurrentStatus())) {
            kafkaListenerEndpointRegistry.getListenerContainer(constantProvider.LISTENER_ID).start();
        }
    }
}

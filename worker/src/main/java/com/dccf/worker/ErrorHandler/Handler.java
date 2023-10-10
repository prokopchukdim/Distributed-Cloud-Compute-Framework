package com.dccf.worker.ErrorHandler;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Entity.TaskEntity;
import com.dccf.worker.Model.Status;
import com.dccf.worker.Repository.TaskRepository;
import com.dccf.worker.Service.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@Service
@Slf4j
public class Handler {
    @Autowired
    WorkerService workerService;
    @Autowired
    TaskRepository taskRepository;

    private void cleanUpJobStatus(){
        workerService.setCurrentStatus(JobStatus.ERROR);

        // Update current task status in DB if there is a job in process.
        if (workerService.getCurrentTaskEntity() != null) {
            try {
                TaskEntity taskEntity = workerService.getCurrentTaskEntity();
                taskEntity.setStatus(Status.ERROR);
                taskRepository.save(taskEntity);
            } catch (Exception e2) {
                log.error("Failed to update current task with error status: {}", e2.getMessage());
            }
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException e) {
        cleanUpJobStatus();
        return new ResponseEntity<>(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleIOException(IOException e) {
        cleanUpJobStatus();
        return new ResponseEntity<>(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

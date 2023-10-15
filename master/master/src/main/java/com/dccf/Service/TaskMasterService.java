package com.dccf.Service;

import com.dccf.Entity.FileEntity;
import com.dccf.Entity.TaskEntity;
import com.dccf.Model.FileType;
import com.dccf.Model.Status;
import com.dccf.Model.Task;
import com.dccf.Repository.FileRepository;
import com.dccf.Repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class TaskMasterService {

    private FileRepository fileRepository;
    private TaskRepository taskRepository;
    private KafkaProducerService kafkaProducerService;

    /**
     * Insert a job into the job queue
     * @param dockerFile Dockerfile for job
     * @param taskFiles Array of files necessary for the Dockerfile to execute the task.
     * @return Job ID
     */
    public long insertIntoQueue(MultipartFile dockerFile, MultipartFile[] taskFiles) {
        TaskEntity taskEntity = saveInfoToDb(dockerFile, taskFiles);
        kafkaProducerService.submitTask(Long.toString(taskEntity.getTaskId()));
        return taskEntity.getTaskId();
    }

    @Transactional
    private TaskEntity saveInfoToDb(MultipartFile dockerFile, MultipartFile[] taskFiles) {
        // Save task entity
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setStatus(Status.SUBMITTED);
        taskRepository.save(taskEntity);

        // Save docker file
        FileEntity dockerEntity = new FileEntity();
        dockerEntity.setTaskEntity(taskEntity);
        dockerEntity.setFileType(FileType.DOCKER);
        dockerEntity.setName(dockerFile.getName());
        try {
            dockerEntity.setFileData(dockerFile.getBytes());
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
        fileRepository.save(dockerEntity);

        // Save task files
        for (MultipartFile f: taskFiles){
            FileEntity taskFile = new FileEntity();
            taskFile.setFileType(FileType.TASK);
            taskFile.setTaskEntity(taskEntity);
            taskFile.setName(f.getName());
            try {
                taskFile.setFileData(f.getBytes());
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
            fileRepository.save(taskFile);
        }
        return taskEntity;
    }

    /**
     * Get status of a current job
     * @param jobId identifier of job. Returned when creating a job.
     * @return Job status.
     */
    public Status getJobStatus(long jobId) {
        Optional<TaskEntity> entity = taskRepository.findById(jobId);
        if (entity.isEmpty()) {
            throw new NoSuchElementException("Failed to find task by given id");
        }
        return entity.get().getStatus();
    }

    /**
     * Get resulting files of job
     * @param jobId
     * @return
     */
    public List<FileEntity> getResultingFiles(long jobId) {
        return fileRepository.findAllResultsByTaskId(jobId);
    }
}

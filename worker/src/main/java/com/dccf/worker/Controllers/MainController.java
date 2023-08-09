package com.dccf.worker.Controllers;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Service.WorkerService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Controller
@Slf4j
@AllArgsConstructor
public class MainController {

    private WorkerService workerService;

    @Getter
    @Setter
    private JobStatus currentStatus = JobStatus.NO_JOB;

    /**
     * Submit a job to the worker.
     * For any job to have return files, they must be placed in the ./output directory
     * @param dockerFile
     * @param taskFiles
     * @return job status
     */
    @RequestMapping(value = "/submitJob", method = RequestMethod.POST)
    ResponseEntity<JobStatus> submitJob(@RequestParam() MultipartFile dockerFile, @RequestParam() List<MultipartFile> taskFiles, @RequestParam(required = false) Boolean override) throws IOException, InterruptedException {
        // If there's already a job and override is False, throw a 400
        if ((override == null || !override) && !JobStatus.NO_JOB.equals(currentStatus)) {
            return ResponseEntity.status(400).body(JobStatus.ERROR);
        }

        workerService.killDockerProcesses();
        currentStatus = JobStatus.PROCESSING;
        workerService.buildAndRunImage(dockerFile, taskFiles);
        return ResponseEntity.status(200).body(currentStatus);
    }

    /**
     *
     * @return job status
     */
    @RequestMapping(value = "/getStatus", method = RequestMethod.GET)
    ResponseEntity<JobStatus> getStatus() {
        if (JobStatus.RUNNING.equals(currentStatus)) {
            try {
                JobStatus realStatus = workerService.checkForCompletion();
                currentStatus = realStatus;
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return ResponseEntity.status(200).body(currentStatus);
    }

    /**
     * Get results if the docker container has stopped, otherwise returns 405.
     * If the container has finished, removes the container and image from the worker.
     * @return the '/output' directory as a java File
     */
    @RequestMapping(value = "/getResult", method = RequestMethod.GET)
    ResponseEntity<File> getResult() throws IOException, InterruptedException {
        if (!JobStatus.EXITED.equals(currentStatus)){
            return ResponseEntity.status(405).body(null);
        }
        File responseFile = workerService.getResponseFiles();
        workerService.killDockerProcesses();
        currentStatus = JobStatus.NO_JOB;
        return ResponseEntity.status(200).body(responseFile);
    }

    /**
     *
     * @return logs
     */
    @RequestMapping(value = "/getLogs", method = RequestMethod.GET)
    ResponseEntity<List<Resource>> getLogs() {
        // TODO should just return the whole directory containing the logs
        List<Resource> logs;
        try {
            logs = workerService.getLogs();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        return ResponseEntity.status(200).body(logs);
    }

    /**
     * Kill all docker processes, delete all containers and images.
     * @return
     */
    @RequestMapping(value = "/killJob", method = RequestMethod.POST)
    ResponseEntity<JobStatus> killJob() throws IOException, InterruptedException {
        workerService.killDockerProcesses();
        currentStatus = JobStatus.NO_JOB;
        return ResponseEntity.status(200).body(currentStatus);
    }
}

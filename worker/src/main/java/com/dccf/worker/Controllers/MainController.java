package com.dccf.worker.Controllers;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Service.WorkerService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@Slf4j
@AllArgsConstructor
public class MainController {

    private WorkerService workerService;


    @Getter
    @Setter
    private JobStatus currentStatus = JobStatus.NO_JOB;
    private List<MultipartFile> responseFiles = new ArrayList<>();

    @RequestMapping(value = "/submitJob", method = RequestMethod.POST)
    ResponseEntity<JobStatus> submitJob(MultipartFile dockerFile, List<MultipartFile> taskFiles) {
        currentStatus = JobStatus.PROCESSING;
        workerService.buildAndRunImage(dockerFile, taskFiles);
        return ResponseEntity.status(200).body(currentStatus);
    }

    @RequestMapping(value = "/checkStatus", method = RequestMethod.GET)
    ResponseEntity<JobStatus> getStatus() {
        return ResponseEntity.status(200).body(currentStatus);
    }

    @RequestMapping(value = "/getResult", method = RequestMethod.GET)
    ResponseEntity<List<MultipartFile>> getResult() {
        if (!JobStatus.COMPLETE.equals(currentStatus)){
            return ResponseEntity.status(405).body(null);
        }
        currentStatus = JobStatus.NO_JOB;
        return ResponseEntity.status(200).body(responseFiles);
    }

    @RequestMapping(value = "/getLogs", method = RequestMethod.GET)
    ResponseEntity<List<MultipartFile>> getLogs() {
        List<MultipartFile> logs;
        try {
            logs = workerService.getLogs();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        return ResponseEntity.status(200).body(logs);
    }

}

package com.dccf.Controllers;

import com.dccf.Entity.FileEntity;
import com.dccf.Model.Status;
import com.dccf.Repository.TaskRepository;
import com.dccf.Service.TaskMasterService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@Slf4j
public class MainController {
    @Autowired
    TaskMasterService taskMasterService;

    @Autowired
    TaskRepository taskRepository;


    @RequestMapping("/")
    String hello() {
        return "Working";
    }

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    ResponseEntity<Long> submitJob(MultipartFile dockerFile, MultipartFile[] taskFiles){
        log.info("Received request to submit job");
        return ResponseEntity.status(200).body(taskMasterService.insertIntoQueue(dockerFile, taskFiles));
    }

    @RequestMapping("/getJobStatus")
    ResponseEntity<Status> getStatus(long jobId) {
        log.info("Received request to get status of job: {}", jobId);
        return ResponseEntity.status(200).body(taskMasterService.getJobStatus(jobId));
    }

    @RequestMapping("/getResultingFiles")
    ResponseEntity<List<FileEntity>> getResults(long jobId){
        log.info("Received request to get results of job: {}", jobId);
        // Verify that job exists first
        taskMasterService.getJobStatus(jobId);

        return ResponseEntity.status(200).body(taskMasterService.getResultingFiles(jobId));
    }

    @RequestMapping("/testSQL")
    ResponseEntity<String> test() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return ResponseEntity.status(200).body(objectMapper.writeValueAsString(taskRepository.findAll()));
    }

    @RequestMapping(value = "/submitDemo", method = RequestMethod.POST)
    ResponseEntity<Long> submitTestJob() throws IOException {
        log.info("Submitting a mock job");
        File dockerFile = new File("/DemoResources/dockerfile");
        File entryPoint = new File("/DemoResources/entrypoint.sh");

        if (!dockerFile.exists() || !entryPoint.exists()) {
            throw new RuntimeException("Failed to load mock resources. Dockerfile exists: " + dockerFile.exists() + "; entrypoint.sh exists: " + entryPoint.exists());
        }

        MultipartFile dockerFileMultipart = new MockMultipartFile("dockerfile", FileUtils.readFileToByteArray(dockerFile));
        MultipartFile entryPointMultipart = new MockMultipartFile("entrypoint.sh", FileUtils.readFileToByteArray(entryPoint));
        MultipartFile[] taskFiles = {entryPointMultipart};

        return ResponseEntity.status(200).body(taskMasterService.insertIntoQueue(dockerFileMultipart, taskFiles));
    }
}

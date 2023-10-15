package com.dccf.Controllers;

import com.dccf.Repository.TaskRepository;
import com.dccf.Repository.TestRepository;
import com.dccf.Service.TaskMasterService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@Slf4j
public class MainController {
    @Autowired
    TaskMasterService taskMasterService;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TestRepository testRepository;

    @RequestMapping("/")
    String hello() {
        return "Working";
    }

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    ResponseEntity<String> submitJob(MultipartFile dockerFile, MultipartFile[] taskFiles){
        log.info("Received request to submit job");
        taskMasterService.insertIntoQueue(dockerFile, taskFiles);
        return ResponseEntity.status(200).body("ok");
    }

    @RequestMapping("/testSQL/")
    ResponseEntity<String>  test() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return ResponseEntity.status(200).body(objectMapper.writeValueAsString(taskRepository.findAll()));
    }

    @RequestMapping("/testInsert/")
    @Transactional
    ResponseEntity<String> testTest() {
        testRepository.insertNewMisc("misc");
        return ResponseEntity.status(200).body("ok");
    }

    @RequestMapping("/testInsertRetrieve")
    ResponseEntity<String> testTestRetreive() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return ResponseEntity.status(200).body(objectMapper.writeValueAsString(testRepository.findAll()));
    }

    @RequestMapping(value = "/submitDemo", method = RequestMethod.POST)
    ResponseEntity<String> submitTestJob() throws IOException {
        // TODO should return generated job ID
        log.info("Submitting a mock job");
        File dockerFile = new File("/DemoResources/dockerfile");
        File entryPoint = new File("/DemoResources/entrypoint.sh");

        if (!dockerFile.exists() || !entryPoint.exists()) {
            throw new RuntimeException("Failed to load mock resources. Dockerfile exists: " + dockerFile.exists() + "; entrypoint.sh exists: " + entryPoint.exists());
        }

        MultipartFile dockerFileMultipart = new MockMultipartFile("dockerfile", FileUtils.readFileToByteArray(dockerFile));
        MultipartFile entryPointMultipart = new MockMultipartFile("entrypoint.sh", FileUtils.readFileToByteArray(entryPoint));
        MultipartFile[] taskFiles = {entryPointMultipart};
        taskMasterService.insertIntoQueue(dockerFileMultipart, taskFiles);
        return ResponseEntity.status(200).body("ok");
    }
}

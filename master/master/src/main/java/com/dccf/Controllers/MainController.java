package com.dccf.Controllers;

import com.dccf.Service.TaskMasterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

@RestController
@Slf4j
public class MainController {
    @Autowired
    TaskMasterService taskMasterService;

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

    @RequestMapping(value = "/submitTest", method = RequestMethod.POST)
    ResponseEntity<String> submitTestJob(){
        log.info("Submitting a mock job");
        MultipartFile dockerFile = new MockMultipartFile("Dockerfile", new byte[]{'d','o','c','k'});
        MultipartFile taskFile1 = new MockMultipartFile("taskfile1", new byte[]{'1'});
        MultipartFile taskFile2 = new MockMultipartFile("taskfile2", new byte[]{'2'});
        MultipartFile[] taskFiles = {taskFile1, taskFile2};
        taskMasterService.insertIntoQueue(dockerFile, taskFiles);
        return ResponseEntity.status(200).body("ok");
    }
}

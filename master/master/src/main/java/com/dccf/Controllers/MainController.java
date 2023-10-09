package com.dccf.Controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class MainController {

    @RequestMapping("/")
    String hello() {
        return "Working";
    }

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    ResponseEntity<String> submitJob(MultipartFile dockerFile, List<MultipartFile> taskFiles){

        return ResponseEntity.status(200).body("ok");
    }
}

package com.dccf.Model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@Data
public class Task {
    private List<MultipartFile> taskFiles;
    private MultipartFile runDockerFile;
    private UUID uuid;
    private String workerIdentifier;
    private Status status = Status.SUBMITTED;
    private MultipartFile log;
    private List<MultipartFile> returnFiles;

    private List<MultipartFile> getReturn() {
        if (status.equals(Status.COMPLETE)) {
            return returnFiles;
        }
        return null;
    }
}

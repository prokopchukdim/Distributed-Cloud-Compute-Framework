package com.dccf.Model;

import lombok.Data;

import java.io.File;
import java.util.*;

@Data
public class Task {
    private List<File> taskFiles;
    private File runDockerFile;
    private UUID uuid;
    private String workerIdentifier;
    private Status status = Status.SUBMITTED;
    private File log;
    private List<File> returnFiles;

    private List<File> getReturn() {
        if (status.equals(Status.COMPLETE)) {
            return taskFiles;
        }
        return null;
    }
}

package com.dccf.worker.Service;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Controllers.MainController;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
@Slf4j
public class WorkerService {

    private List<MultipartFile> taskFiles = new ArrayList<>();
    private MultipartFile dockerFile;
    private List<MultipartFile> responseFiles = new ArrayList<>();

    private final String WORKDIR = "workDir";
    private final Path dockerPath = Paths.get(WORKDIR);
    private final MainController mainController;

    /**
     * Build and run the job. Updates job status.
     * @param dockerFile
     */
    public void buildAndRunImage(MultipartFile dockerFile, List<MultipartFile> taskFiles) {
        this.taskFiles = taskFiles;
        this.dockerFile = dockerFile;

        // Create a directory for the dockerFile and taskFiles
        File workDir = new File("./" + WORKDIR);
        if (workDir.exists()) {
            workDir.delete();
        }
        workDir.mkdirs();
        try {
            Files.copy(dockerFile.getInputStream(), dockerPath.resolve("dockerfile"), StandardCopyOption.REPLACE_EXISTING);
            for (MultipartFile file : taskFiles) {
                Files.copy(file.getInputStream(), dockerPath.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Error copying user input files to workdir: ", e);
            throw new RuntimeException(e.getMessage());
        }

        mainController.setCurrentStatus(JobStatus.PROCESSING);
        //Build dockerfile
        String buildCommand[] = new String[]{"docker build -t job ./" + WORKDIR};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .directory(workDir)
                .redirectOutput(new File("buildStdout.txt"))
                .redirectError(new File("buildStderr.txt"));
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        CompletableFuture<Process> future = process.onExit();
        future.handle((res, ex) -> {
            if (ex != null) {
                mainController.setCurrentStatus(JobStatus.ERROR);
                log.error("Error during docker build: ", ex);
            }
            return res;
        });

        //Run dockerfile after build is complete
        future.thenRun(() -> {

            if (JobStatus.ERROR.equals(mainController.getCurrentStatus())) {
                log.info("Error during docker build, skipping docker run");
                return;
            }

            String runCommand[] = new String[]{"docker run job" };
            ProcessBuilder runBuilder = new ProcessBuilder(runCommand)
                    .directory(workDir)
                    .redirectOutput(new File("runStdout.txt"))
                    .redirectError(new File("runStderr.txt"));
            try {
                mainController.setCurrentStatus(JobStatus.RUNNING);
                runBuilder.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Get logs for current job
     * @return logs
     * @throws IOException
     */
    public List<Resource> getLogs() throws IOException {
        String[] logPaths = {"/buildStdout.txt", "/buildStderr.txt", "/runStdout.txt", "/runStderr.txt"};
        List<Resource> logList = new ArrayList<>();
        for (String fileName : logPaths) {
            Path filePath = Paths.get("./" + WORKDIR + fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                logList.add(resource);
            }
            else {
                log.debug("Could not resolve log file: {}", fileName);
            }
        }
        return new ArrayList<>();
    }

    /**
     * Check for the completion of the job
     * @return Status of job (either RUNNING or EXITED)
     * @throws IOException
     * @throws InterruptedException
     */
    public JobStatus checkForCompletion() throws IOException, InterruptedException {
        //List current running processes
        String buildCommand[] = new String[]{"docker ps -q"};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .redirectOutput(new File("./ps-result.txt"));
        Process p = processBuilder.start();
        p.waitFor();

        File res = new File("./ps-result.txt");
        boolean serviceRunning = false;
        try {
            Scanner scanner = new Scanner(res);
            while (scanner.hasNextLine()) {
                String data = scanner.nextLine();
                if (StringUtils.hasText(data.trim())) {
                    serviceRunning = true;
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            log.error("There was some issue running docker ps, no output file found");
            throw new RuntimeException("Internal issue reading process status");
        }
        return serviceRunning ? JobStatus.RUNNING : JobStatus.EXITED;
    }

    public void killDockerProcesses() throws IOException, InterruptedException {
        // Kill processes, remove stopped containers, and delete all images
        String buildCommand[] = new String[]{"docker kill $(docker ps -q) && docker rm $(docker ps -a -q) && docker rmi $(docker images -q)"};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .redirectOutput(new File("./ps-result.txt"));
        Process p = processBuilder.start();
        p.waitFor();
    }
}

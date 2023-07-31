package com.dccf.worker.Service;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Controllers.MainController;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
     * @throws IOException
     */
    public void buildAndRunImage(MultipartFile dockerFile, List<MultipartFile> taskFiles) {
        //TODO add taskFiles to working directory
        this.taskFiles = taskFiles;

        // Create a directory for the dockerFile
        File workDir = new File("./" + WORKDIR);
        if (workDir.exists()) {
            workDir.delete();
        }
        workDir.mkdirs();
        try {
            Files.copy(dockerFile.getInputStream(), dockerPath.resolve("dockerfile"));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        mainController.setCurrentStatus(JobStatus.PROCESSING);
        //Build dockerfile
        String buildCommand[] = new String[]{"docker build -t job ./" + WORKDIR};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .directory(workDir)
                .redirectOutput(new File(workDir + "/buildStdout.txt"))
                .redirectError(new File(workDir + "/buildStderr.txt"));
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
                    .redirectOutput(new File(workDir + "/runStdout.txt"))
                    .redirectError(new File(workDir + "/runStderr.txt"));
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
    public List<MultipartFile> getLogs() throws IOException {
        return new ArrayList<>();
    }
}

package com.dccf.worker.Service;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Entity.FileEntity;
import com.dccf.worker.Entity.TaskEntity;
import com.dccf.worker.Model.FileType;
import com.dccf.worker.Model.Status;
import com.dccf.worker.Repository.FileRepository;
import com.dccf.worker.Repository.TaskRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class WorkerService {

    @Autowired
    FileRepository fileRepository;
    @Autowired
    TaskRepository taskRepository;


    private List<MultipartFile> responseFiles = new ArrayList<>();

    private final String WORKDIR = "/data/workdir/";
    private final Path dockerPath = Paths.get(WORKDIR);

    @Getter
    @Setter
    private JobStatus currentStatus = JobStatus.NO_JOB;

    @Getter
    private TaskEntity currentTaskEntity = null;

    /**
     * Push a job to the worker. Will search for relevant files in DB and initiate job
     * @param jobId The ID of the job in PostgreSQL
     */
    public void pushJob(String jobId) throws IOException {
        setCurrentStatus(JobStatus.PROCESSING);
        this.currentTaskEntity = null;
        long taskId = Long.valueOf(jobId);

        var optionalTaskEntity = taskRepository.findById(taskId);
        if (!optionalTaskEntity.isPresent()) {
            throw new RuntimeException("Could not task in DB");
        }
        TaskEntity taskEntity = optionalTaskEntity.get();
        this.currentTaskEntity = taskEntity;

        taskEntity.setStatus(Status.PROCESSING);
        taskEntity.setWorkerIdentifier(System.getenv("POD_NAME"));
        taskRepository.save(taskEntity);

        List<FileEntity> fileEntities = fileRepository.findAllByTaskId(taskId);
        MultipartFile dockerFile = null;
        List<MultipartFile> taskFiles = new ArrayList<>();
        for (var f: fileEntities) {
            if (f.getFileType() == FileType.DOCKER) {
                dockerFile = new MockMultipartFile(f.getName(), f.getFileData());
            } else if (f.getFileType() == FileType.TASK) {
                taskFiles.add(new MockMultipartFile(f.getName(), f.getFileData()));
            }
        }
        if (dockerFile == null) {
            throw new RuntimeException("Could not find valid Dockerfile");
        }
        buildAndRunImage(dockerFile, taskFiles);
    }


    /**
     * Build and run the job. Updates job status.
     * @param dockerFile
     */
    public void buildAndRunImage(MultipartFile dockerFile, List<MultipartFile> taskFiles) throws IOException {

        // Create a directory for the dockerFile and taskFiles
        File workDir = new File(WORKDIR);
        if (workDir.exists()) {
            FileUtils.cleanDirectory(workDir);
            workDir.delete();
        }
        workDir.mkdirs();

        //create output directory
        File outputDir = new File(WORKDIR + "output");
        outputDir.mkdirs();
        try {
            Files.copy(dockerFile.getInputStream(), dockerPath.resolve("dockerfile"), StandardCopyOption.REPLACE_EXISTING);
            for (MultipartFile file : taskFiles) {
                Files.copy(file.getInputStream(), dockerPath.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Error copying user input files to workdir: ", e);
            throw new RuntimeException(e.getMessage());
        }

        currentStatus = JobStatus.PROCESSING;
        //Build dockerfile
        String[] buildCommand = new String[]{"docker build -t job " + WORKDIR};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .directory(workDir)
                .redirectOutput(new File("output/logs/buildStdout.txt"))
                .redirectError(new File("output/logs/buildStderr.txt"));
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        CompletableFuture<Process> future = process.onExit();
        future.handle((res, ex) -> {
            if (ex != null) {
                currentStatus = JobStatus.ERROR;
                log.error("Error during docker build: ", ex);
            }
            return res;
        });

        //Run dockerfile after build is complete
        future.thenRun(() -> {

            if (JobStatus.ERROR.equals(currentStatus)) {
                log.info("Error during docker build, skipping docker run");
                return;
            }

            String[] runCommand = new String[]{"docker run job" };
            ProcessBuilder runBuilder = new ProcessBuilder(runCommand)
                    .directory(outputDir)
                    .redirectOutput(new File("logs/runStdout.txt"))
                    .redirectError(new File("logs/runStderr.txt"));
            try {
                currentStatus = JobStatus.RUNNING;
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
            Path filePath = Paths.get(WORKDIR + fileName);
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
        String[]  buildCommand = new String[]{"docker ps -q"};
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

    /**
     * Kills all docker processes
     * @throws IOException
     * @throws InterruptedException
     */
    public void killDockerProcesses() throws IOException, InterruptedException {
        // Kill processes, remove stopped containers, and delete all images
        String[] buildCommand = new String[]{"docker kill $(docker ps -q) && docker rm $(docker ps -a -q) && docker rmi $(docker images -q)"};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .redirectOutput(new File("./ps-result.txt"));
        Process p = processBuilder.start();
        p.waitFor();
    }

    /**
     * Get output directory and return it if it exists
     * @return
     * @throws MalformedURLException
     */
    public File getResponseFiles() throws MalformedURLException {
        File output = new File("./" + WORKDIR + "/output");
        return output.isDirectory() ? output : null;
    }
}

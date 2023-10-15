package com.dccf.worker.Service;

import com.dccf.worker.Const.JobStatus;
import com.dccf.worker.Entity.FileEntity;
import com.dccf.worker.Entity.TaskEntity;
import com.dccf.worker.Model.FileType;
import com.dccf.worker.Model.Status;
import com.dccf.worker.Repository.FileRepository;
import com.dccf.worker.Repository.TaskRepository;
import jakarta.transaction.Transactional;
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

    // Changing the working directory will require updating the docker run command for
    // running the custom job. Moreover, the kubernetes config for the worker will need an update
    // since the job output directory is actually hosted on the docker daemon container and linked to the
    // worker container (see the job-output volumeMount).
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
        long taskId = Long.valueOf(jobId);
        confirmTaskInDB(taskId);

        List<FileEntity> fileEntities = getTaskFiles(taskId);
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

    @Transactional
    List<FileEntity> getTaskFiles(long taskId) {
        return fileRepository.findAllByTaskId(taskId);
    }

    /**
     * Update task info in DB with consumer id
     */
    @Transactional
    private void confirmTaskInDB(long taskId) {
        setCurrentStatus(JobStatus.PROCESSING);
        this.currentTaskEntity = null;

        var optionalTaskEntity = taskRepository.findById(taskId);
        if (!optionalTaskEntity.isPresent()) {
            throw new RuntimeException("Could not find task in DB");
        }
        TaskEntity taskEntity = optionalTaskEntity.get();
        this.currentTaskEntity = taskEntity;

        taskEntity.setStatus(Status.PROCESSING);
        taskEntity.setWorkerIdentifier(System.getenv("POD_NAME"));
        taskRepository.save(taskEntity);
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
//        File outputDir = new File(WORKDIR + "output/");
        File outputDir = new File("/data/output");
        if (outputDir.exists()) {
            FileUtils.cleanDirectory(outputDir);
        }
        else {
            outputDir.mkdirs();
        }

        try {
            Files.copy(dockerFile.getInputStream(), dockerPath.resolve("dockerfile"), StandardCopyOption.REPLACE_EXISTING);
            for (MultipartFile file : taskFiles) {
                Files.copy(file.getInputStream(), dockerPath.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Error copying user input files to workdir: ", e);
            throw new RuntimeException(e.getMessage());
        }

        //Create build logs
        File logDir = new File(workDir, "logs/");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        File buildOut = new File(logDir,"buildStdout.txt");
        buildOut.createNewFile();
        File buildErr = new File(logDir,"buildStderr.txt");
        buildErr.createNewFile();

        //Build dockerfile
        String[] buildCommand = new String[]{"docker", "build", ".","-t","job"};
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand)
                .directory(workDir)
                .redirectOutput(buildOut)
                .redirectError(buildErr);
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
                currentTaskEntity.setStatus(Status.ERROR);
                taskRepository.save(currentTaskEntity);
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

            log.info("Running current task");

            File runOut = new File(logDir,"runStdout.txt");
            File runErr = new File(logDir,"runStderr.txt");
            try {
                runOut.createNewFile();
                runErr.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            String[] runCommand = new String[]{"docker", "run", "-v", "/data/output:/output", "job"};
            ProcessBuilder runBuilder = new ProcessBuilder(runCommand)
                    .directory(outputDir)
                    .redirectOutput(runOut)
                    .redirectError(runErr);
            Process runProcess;
            try {
                currentStatus = JobStatus.RUNNING;
                runProcess = runBuilder.start();
            } catch (IOException e) {
                currentStatus = JobStatus.ERROR;
                currentTaskEntity.setStatus(Status.ERROR);
                taskRepository.save(currentTaskEntity);
                throw new RuntimeException(e);
            }

            CompletableFuture<Process> runFuture = runProcess.onExit();
            runFuture.handle((res, ex) -> {
                if (ex != null) {
                    currentStatus = JobStatus.ERROR;
                    currentTaskEntity.setStatus(Status.ERROR);
                    taskRepository.save(currentTaskEntity);
                    log.error("Error during docker run: ", ex);
                }
                else {
                    try {
                        log.info("Completed current task, saving results");

                        List<MultipartFile> outputFiles = getFileList(outputDir);
                        List<MultipartFile> logFiles = getFileList(logDir);
                        saveFilesToRepo(logFiles, FileType.LOG);
                        saveFilesToRepo(outputFiles, FileType.RETURN);

                        currentStatus = JobStatus.EXITED;
                        currentTaskEntity.setStatus(Status.COMPLETE);
                        taskRepository.save(currentTaskEntity);
                        log.info("Completed current task, results saved");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return res;
            });
        });
    }

    /**
     * Get list of MultipartFile files from a directory dir.
     */
    private List<MultipartFile> getFileList(File dir) throws IOException {
        if (dir == null) {
            throw new NullPointerException();
        }

        List<MultipartFile> fileList = new ArrayList<>();
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                fileList.add(new MockMultipartFile(f.getName(), org.apache.commons.io.FileUtils.readFileToByteArray(f)));
            }
        }
        else {
            throw new RuntimeException("Failed to get files from" + dir.getName());
        }
        return fileList;
    }

    @Transactional
    private void saveFilesToRepo(List<MultipartFile> files, FileType fileType) throws IOException {
        List<FileEntity> fileEntities = new ArrayList<>();
        for (MultipartFile f : files) {
            FileEntity taskFile = new FileEntity();
            taskFile.setFileType(fileType);
            taskFile.setTaskEntity(currentTaskEntity);
            taskFile.setFileData(f.getBytes());
            taskFile.setName(f.getName());
            fileEntities.add(taskFile);
        }
        fileRepository.saveAll(fileEntities);
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
        return logList;
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

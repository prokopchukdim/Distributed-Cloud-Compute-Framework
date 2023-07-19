package com.dccf.Service;

import com.dccf.Model.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TaskMaster {

    List<Task> taskQueue = new ArrayList<>();

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    private void handleTaskQueue(){

        //todo Sync task queue with Redis

        if (!taskQueue.isEmpty()) {
            //check if there are free workers and send tasks to workers
        }
    }

    private void insertIntoQueue(Task task) {
        //todo insert task into Redis queue
    }

}

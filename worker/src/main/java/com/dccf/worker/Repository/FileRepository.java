package com.dccf.worker.Repository;

import com.dccf.worker.Entity.FileEntity;
import com.dccf.worker.Entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    @Query(value = "SELECT f.file_id, f.type, f.task_entity_task_id, f.name, f.file_data from file_table f where f.task_entity_task_id = :taskId", nativeQuery = true)
    List<FileEntity> findAllByTaskId(@Param("taskId") long taskId);
}

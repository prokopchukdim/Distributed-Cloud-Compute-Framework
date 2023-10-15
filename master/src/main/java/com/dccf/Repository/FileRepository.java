package com.dccf.Repository;

import com.dccf.Entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    @Query(value = "SELECT f.file_id, f.type, f.task_entity_task_id, f.name, f.file_data FROM file_table f WHERE f.task_entity_task_id = :taskId AND f.type IN (2, 3)", nativeQuery = true)
    List<FileEntity> findAllResultsByTaskId(@Param("taskId") long taskId);
}

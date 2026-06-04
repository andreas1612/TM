package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.TaskChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Integer> {

    List<TaskChecklistItem> findByTask_TaskIdOrderBySortOrderAscChecklistItemIdAsc(Integer taskId);
}
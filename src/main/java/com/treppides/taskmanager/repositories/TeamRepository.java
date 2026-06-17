package com.treppides.taskmanager.repositories;

import com.treppides.taskmanager.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Integer> {
}

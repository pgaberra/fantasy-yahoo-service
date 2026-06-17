package com.fantasy.yahoo.players;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findAllByOrderByStartedAtDesc(Limit limit);
}

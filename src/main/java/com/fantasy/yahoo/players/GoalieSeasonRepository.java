package com.fantasy.yahoo.players;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalieSeasonRepository extends JpaRepository<GoalieSeason, PlayerSeasonId> {

    List<GoalieSeason> findAllBySeason(int season);
}

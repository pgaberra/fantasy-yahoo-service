package com.fantasy.yahoo.players;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkaterSeasonRepository extends JpaRepository<SkaterSeason, PlayerSeasonId> {

    List<SkaterSeason> findAllBySeason(int season);
}

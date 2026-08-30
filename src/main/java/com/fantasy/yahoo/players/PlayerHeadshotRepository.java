package com.fantasy.yahoo.players;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlayerHeadshotRepository extends JpaRepository<PlayerHeadshot, Long> {

    @Query("select new com.fantasy.yahoo.players.HeadshotSource(h.playerId, h.sourceUrl, h.recipe) "
            + "from PlayerHeadshot h")
    List<HeadshotSource> findAllSources();

    @Query("select h.playerId from PlayerHeadshot h")
    List<Long> findAllPlayerIds();
}

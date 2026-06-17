package com.fantasy.yahoo.players;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkaterRepository extends JpaRepository<Skater, Long> {

    List<Skater> findAllByOrderByLastNameAscFirstNameAsc();
}

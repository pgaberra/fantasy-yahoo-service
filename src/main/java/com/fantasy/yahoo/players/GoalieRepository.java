package com.fantasy.yahoo.players;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalieRepository extends JpaRepository<Goalie, Long> {

    List<Goalie> findAllByOrderByLastNameAscFirstNameAsc();
}

package com.fantasy.yahoo.league;

import com.fantasy.yahoo.exception.GlobalExceptionHandler;
import com.fantasy.yahoo.exception.YahooAccessDeniedException;
import com.fantasy.yahoo.oauth.YahooNotConnectedException;
import com.fantasy.yahoo.league.dto.LeagueRosterPlayer;
import com.fantasy.yahoo.league.dto.LeagueRosterTeam;
import com.fantasy.yahoo.league.dto.LeagueRostersResponse;
import com.fantasy.yahoo.player.YahooPlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YahooLeagueController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class YahooLeagueControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private YahooLeagueService leagueService;
    @MockitoBean
    private YahooPlayerService playerService;

    @Test
    void rosters_returnsEachTeamWithItsCurrentPlayers() throws Exception {
        when(leagueService.rosters("user-1", "465.l.1")).thenReturn(new LeagueRostersResponse("465.l.1", List.of(
                new LeagueRosterTeam("465.l.1.t.1", "Alpha", true,
                        List.of(new LeagueRosterPlayer("465.p.6743", 6743, "C"))))));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].teamKey").value("465.l.1.t.1"))
                .andExpect(jsonPath("$.teams[0].mine").value(true))
                .andExpect(jsonPath("$.teams[0].players[0].playerId").value(6743))
                .andExpect(jsonPath("$.teams[0].players[0].selectedPosition").value("C"));
    }

    @Test
    void rosters_rejectsAnOversizedLeagueKeyBeforeAskingYahoo() throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/leagues/" + "x".repeat(65) + "/rosters").param("appUserId", "user-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leagueService);
    }

    @Test
    void rosters_rejectsAnOversizedUserId() throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "u".repeat(129)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leagueService);
    }

    @Test
    void rosters_forAUserWhoHasNotConnectedYahoo_isNotFound() throws Exception {
        when(leagueService.rosters(anyString(), anyString())).thenThrow(new YahooNotConnectedException("user-1"));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rosters_whenYahooRefuses_isForbiddenWithYahoosWording() throws Exception {
        when(leagueService.rosters(anyString(), anyString())).thenThrow(new YahooAccessDeniedException(
                "Yahoo refused the request: This application is not authorized to perform this action.", null));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Yahoo refused the request: This application is not authorized to perform this action."));
    }
}

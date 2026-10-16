package com.fantasy.yahoo.league;

import com.fantasy.yahoo.exception.GlobalExceptionHandler;
import com.fantasy.yahoo.exception.YahooAccessDeniedException;
import com.fantasy.yahoo.oauth.YahooNotConnectedException;
import com.fantasy.yahoo.league.dto.LeagueRosterPlayer;
import com.fantasy.yahoo.league.dto.LeagueRosterTeam;
import com.fantasy.yahoo.league.dto.LeagueRostersResponse;
import com.fantasy.yahoo.player.YahooPlayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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
        when(leagueService.rosters("user-1", "465.l.1", null)).thenReturn(new LeagueRostersResponse("465.l.1", List.of(
                new LeagueRosterTeam("465.l.1.t.1", "Alpha", true,
                        List.of(new LeagueRosterPlayer("465.p.6743", 6743, "C", "Player One", "EDM", 97,
                                false, List.of("C", "LW"), "DTD"))))));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].teamKey").value("465.l.1.t.1"))
                .andExpect(jsonPath("$.teams[0].mine").value(true))
                .andExpect(jsonPath("$.teams[0].players[0].playerId").value(6743))
                .andExpect(jsonPath("$.teams[0].players[0].selectedPosition").value("C"))
                .andExpect(jsonPath("$.teams[0].players[0].eligiblePositions[1]").value("LW"))
                .andExpect(jsonPath("$.teams[0].players[0].status").value("DTD"));
    }

    @Test
    void rosters_passesTheDateThrough() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 14);
        when(leagueService.rosters("user-1", "465.l.1", day))
                .thenReturn(new LeagueRostersResponse("465.l.1", List.of()));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters")
                        .param("appUserId", "user-1").param("date", "2026-10-14"))
                .andExpect(status().isOk());

        verify(leagueService).rosters("user-1", "465.l.1", day);
    }

    @Test
    void rosters_withoutADate_readsTodaysRoster() throws Exception {
        when(leagueService.rosters("user-1", "465.l.1", null))
                .thenReturn(new LeagueRostersResponse("465.l.1", List.of()));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isOk());

        verify(leagueService).rosters("user-1", "465.l.1", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"14-10-2026", "2026-13-01", "2026-10-14;date=2026-10-15", "tomorrow"})
    void rosters_rejectsADateThatIsNotIsoBeforeAskingYahoo(String date) throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters")
                        .param("appUserId", "user-1").param("date", date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'date'"));

        verifyNoInteractions(leagueService);
    }

    @Test
    void freeAgents_passesAnAllowedPositionThrough() throws Exception {
        when(playerService.availablePlayers("user-1", "465.l.1", "G", false, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/free-agents")
                        .param("appUserId", "user-1").param("limit", "50").param("position", "G"))
                .andExpect(status().isOk());

        verify(playerService).availablePlayers("user-1", "465.l.1", "G", false, 50);
    }

    @Test
    void freeAgents_passesWaiversOnlyThrough() throws Exception {
        when(playerService.availablePlayers("user-1", "465.l.1", null, true, 300)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/free-agents")
                        .param("appUserId", "user-1").param("limit", "300").param("waiversOnly", "true"))
                .andExpect(status().isOk());

        verify(playerService).availablePlayers("user-1", "465.l.1", null, true, 300);
    }

    @Test
    void freeAgents_rejectsAPositionOutsideTheAllowListBeforeAskingYahoo() throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/free-agents")
                        .param("appUserId", "user-1").param("position", "G;status=T"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(playerService);
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
        when(leagueService.rosters(anyString(), anyString(), isNull())).thenThrow(new YahooNotConnectedException("user-1"));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rosters_whenYahooRefuses_isForbiddenWithYahoosWording() throws Exception {
        when(leagueService.rosters(anyString(), anyString(), isNull())).thenThrow(new YahooAccessDeniedException(
                "Yahoo refused the request: This application is not authorized to perform this action.", null));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.1/rosters").param("appUserId", "user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Yahoo refused the request: This application is not authorized to perform this action."));
    }
}

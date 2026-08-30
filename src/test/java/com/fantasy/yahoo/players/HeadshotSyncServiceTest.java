package com.fantasy.yahoo.players;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class HeadshotSyncServiceTest {

    private static final String MCDAVID_IMAGE = "https://images.test/mcdavid-2026.png";
    private static final String SHESTERKIN_IMAGE = "https://images.test/shesterkin-2026.png";

    @Mock
    private PlayerHeadshotRepository headshotRepository;

    private MockRestServiceServer imageCdn;
    private HeadshotSyncService headshotSyncService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        imageCdn = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        headshotSyncService = new HeadshotSyncService(headshotRepository, builder.build());
    }

    /**
     * The whole pool is over a thousand players and every source is megabytes, so a routine sync
     * must fetch only what actually changed.
     */
    @Test
    void fetchesOnlyThePlayersWhoseSourceImageChanged() {
        givenStored(current(9L, MCDAVID_IMAGE));
        imageCdn.expect(requestTo(SHESTERKIN_IMAGE)).andRespond(withSuccess(png(), MediaType.IMAGE_PNG));

        HeadshotRefreshResult result = headshotSyncService.refresh(
                Map.of(9L, MCDAVID_IMAGE, 101L, SHESTERKIN_IMAGE));

        imageCdn.verify();
        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(savedHeadshots()).singleElement()
                .satisfies(headshot -> assertThat(headshot.playerId).isEqualTo(101L));
    }

    @Test
    void storesTheThumbnailRatherThanTheSourceImage() {
        givenStored();
        imageCdn.expect(requestTo(MCDAVID_IMAGE)).andRespond(withSuccess(png(), MediaType.IMAGE_PNG));

        headshotSyncService.refresh(Map.of(9L, MCDAVID_IMAGE));

        PlayerHeadshot stored = savedHeadshots().getFirst();
        assertThat(stored.sourceUrl).isEqualTo(MCDAVID_IMAGE);
        assertThat(stored.image.length).isLessThan(png().length);
    }

    @Test
    void dropsTheHeadshotsOfPlayersWhoHaveLeftThePool() {
        givenStored(current(9L, MCDAVID_IMAGE), current(101L, SHESTERKIN_IMAGE));

        HeadshotRefreshResult result = headshotSyncService.refresh(Map.of(9L, MCDAVID_IMAGE));

        verify(headshotRepository).deleteAllById(Set.of(101L));
        assertThat(result.removed()).isEqualTo(1);
    }

    /** One image the CDN will not serve must not cost the rest of the pool its refresh. */
    @Test
    void keepsGoingWhenOneImageCannotBeFetched() {
        givenStored();
        imageCdn.expect(requestTo(MCDAVID_IMAGE)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        imageCdn.expect(requestTo(SHESTERKIN_IMAGE)).andRespond(withSuccess(png(), MediaType.IMAGE_PNG));

        HeadshotRefreshResult result = headshotSyncService.refresh(
                Map.of(9L, MCDAVID_IMAGE, 101L, SHESTERKIN_IMAGE));

        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(savedHeadshots()).singleElement()
                .satisfies(headshot -> assertThat(headshot.playerId).isEqualTo(101L));
    }

    @Test
    void doesNothingWhenEveryStoredHeadshotIsStillCurrent() {
        givenStored(current(9L, MCDAVID_IMAGE));

        HeadshotRefreshResult result = headshotSyncService.refresh(Map.of(9L, MCDAVID_IMAGE));

        imageCdn.verify();
        verify(headshotRepository, never()).saveAll(any());
        assertThat(result.refreshed()).isZero();
    }

    @Test
    void redrawsAThumbnailRenderedByAnOlderRecipe() {
        // The rendering changed but Yahoo's URL did not, so the source comparison alone would leave
        // every player holding a picture drawn the old way for good.
        givenStored(new HeadshotSource(9L, MCDAVID_IMAGE, "head-64"));
        imageCdn.expect(requestTo(MCDAVID_IMAGE)).andRespond(withSuccess(png(), MediaType.IMAGE_PNG));

        HeadshotRefreshResult result = headshotSyncService.refresh(Map.of(9L, MCDAVID_IMAGE));

        imageCdn.verify();
        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(savedHeadshots()).singleElement()
                .satisfies(headshot -> assertThat(headshot.recipe)
                        .isEqualTo(HeadshotThumbnailer.RECIPE));
    }

    /** A stored headshot drawn from that source by the recipe in force now. */
    private static HeadshotSource current(long playerId, String sourceUrl) {
        return new HeadshotSource(playerId, sourceUrl, HeadshotThumbnailer.RECIPE);
    }

    private void givenStored(HeadshotSource... sources) {
        when(headshotRepository.findAllSources()).thenReturn(List.of(sources));
    }

    @SuppressWarnings("unchecked")
    private List<PlayerHeadshot> savedHeadshots() {
        ArgumentCaptor<List<PlayerHeadshot>> saved = ArgumentCaptor.forClass(List.class);
        verify(headshotRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    private static byte[] png() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, 0xFF000000 | (x * 251 + y * 97));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}

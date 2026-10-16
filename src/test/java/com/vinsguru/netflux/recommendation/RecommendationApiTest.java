package com.vinsguru.netflux.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vinsguru.netflux.recommendation.dto.HomeResponse;
import com.vinsguru.netflux.recommendation.dto.MovieSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@AutoConfigureMockRestServiceServer
@Import(SharedRestClientBuilderConfig.class)
class RecommendationApiTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final List<String> GENRES = List.of(
            "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama",
            "Family", "Fantasy", "History", "Horror", "Music", "Mystery", "Romance",
            "Science Fiction", "TV Movie", "Thriller", "War", "Western"
    );

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    MockRestServiceServer mockServer;

    @Test
    @DisplayName("Should aggregate watchlist, popular, latest and genre recommendations")
    void shouldReturnHomeRecommendations() {
        mockServer.expect(requestTo(containsString("/api/users/watchlist")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(USER_ID_HEADER, "42"))
                .andRespond(withSuccess("[1, 2]", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/batch?ids=1,2")))
                .andRespond(withSuccess(moviesJson(1, 2), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/popular?limit=20")))
                .andRespond(withSuccess(moviesJson(3), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/latest?limit=20")))
                .andRespond(withSuccess(moviesJson(4), MediaType.APPLICATION_JSON));
        expectGenreSearches(Map.of("Action", moviesJson(5)));

        var response = getHome("42");

        assertThat(response.watchlist()).extracting(MovieSummary::id).containsExactly(1L, 2L);
        assertThat(response.popular()).extracting(MovieSummary::id).containsExactly(3L);
        assertThat(response.latest()).extracting(MovieSummary::id).containsExactly(4L);
        assertThat(response.byGenre().get("Action")).extracting(MovieSummary::id).containsExactly(5L);
        assertThat(response.watchlist().getFirst()).isEqualTo(new MovieSummary(
                1L,
                "Movie 1",
                "/poster-1.jpg",
                "/backdrop-1.jpg",
                7.5,
                LocalDate.of(2024, 1, 15),
                List.of("Action", "Drama")
        ));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return every configured genre in order, with empty lists for genres without movies")
    void shouldReturnByGenreInFixedOrderWithAllKeys() {
        mockServer.expect(requestTo(containsString("/api/users/watchlist")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/popular?limit=20")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/latest?limit=20")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        expectGenreSearches(Map.of("Science Fiction", moviesJson(7)));

        var response = getHome("42");

        assertThat(response.byGenre()).containsOnlyKeys(GENRES);
        assertThat(response.byGenre().keySet()).containsExactlyElementsOf(GENRES);
        assertThat(response.byGenre().get("Science Fiction")).extracting(MovieSummary::id).containsExactly(7L);
        assertThat(response.byGenre().get("Western")).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should skip the batch call when the watchlist is empty")
    void shouldSkipBatchCallWhenWatchlistEmpty() {
        mockServer.expect(requestTo(containsString("/api/users/watchlist")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/popular?limit=20")))
                .andRespond(withSuccess(moviesJson(3), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("/api/movies/latest?limit=20")))
                .andRespond(withSuccess(moviesJson(4), MediaType.APPLICATION_JSON));
        expectGenreSearches(Map.of());

        var response = getHome("42");

        assertThat(response.watchlist()).isEmpty();
        assertThat(response.popular()).extracting(MovieSummary::id).containsExactly(3L);
        assertThat(response.latest()).extracting(MovieSummary::id).containsExactly(4L);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return an empty list when an upstream response has no body")
    void shouldReturnEmptyListWhenUpstreamBodyIsEmpty() {
        mockServer.expect(requestTo(containsString("/api/users/watchlist")))
                .andRespond(withSuccess());
        mockServer.expect(requestTo(containsString("/api/movies/popular?limit=20")))
                .andRespond(withSuccess());
        mockServer.expect(requestTo(containsString("/api/movies/latest?limit=20")))
                .andRespond(withSuccess(moviesJson(4), MediaType.APPLICATION_JSON));
        expectGenreSearches(Map.of());

        var response = getHome("42");

        assertThat(response.watchlist()).isEmpty();
        assertThat(response.popular()).isEmpty();
        assertThat(response.latest()).extracting(MovieSummary::id).containsExactly(4L);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return 401 ProblemDetail when X-User-Id header is missing")
    void shouldReturn401WhenUserIdHeaderMissing() {
        var problem = restTestClient.get()
                .uri("/api/recommendations/home")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when X-User-Id header is not numeric")
    void shouldReturn400WhenUserIdNotNumeric() {
        var problem = getHomeExpectingBadRequest("abc");

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when X-User-Id header is not positive")
    void shouldReturn400WhenUserIdNotPositive() {
        var problem = getHomeExpectingBadRequest("0");

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        mockServer.verify();
    }

    private HomeResponse getHome(String userId) {
        return restTestClient.get()
                .uri("/api/recommendations/home")
                .header(USER_ID_HEADER, userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(HomeResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ProblemDetail getHomeExpectingBadRequest(String userId) {
        return restTestClient.get()
                .uri("/api/recommendations/home")
                .header(USER_ID_HEADER, userId)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();
    }

    private void expectGenreSearches(Map<String, String> responsesByGenre) {
        GENRES.forEach(genre -> mockServer.expect(requestTo(containsString(
                        "/api/movies/search?genre=" + UriUtils.encode(genre, StandardCharsets.UTF_8) + "&limit=20")))
                .andRespond(withSuccess(responsesByGenre.getOrDefault(genre, "[]"), MediaType.APPLICATION_JSON)));
    }

    private static String moviesJson(long... ids) {
        var movies = Arrays.stream(ids)
                .mapToObj(RecommendationApiTest::movieJson)
                .toList();
        return "[" + String.join(",", movies) + "]";
    }

    private static String movieJson(long id) {
        return """
                {
                  "id": %d,
                  "title": "Movie %d",
                  "posterPath": "/poster-%d.jpg",
                  "backdropPath": "/backdrop-%d.jpg",
                  "voteAverage": 7.5,
                  "releaseDate": "2024-01-15",
                  "genres": ["Action", "Drama"]
                }
                """.formatted(id, id, id, id);
    }
}

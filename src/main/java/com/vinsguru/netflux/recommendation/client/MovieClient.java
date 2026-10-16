package com.vinsguru.netflux.recommendation.client;

import com.vinsguru.netflux.recommendation.dto.MovieSummary;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MovieClient {

    private static final ParameterizedTypeReference<List<MovieSummary>> MOVIE_LIST = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public MovieClient(RestClient movieRestClient) {
        this.restClient = movieRestClient;
    }

    public List<MovieSummary> getPopular(int limit) {
        return get(restClient.get().uri("/api/movies/popular?limit={limit}", limit));
    }

    public List<MovieSummary> getLatest(int limit) {
        return get(restClient.get().uri("/api/movies/latest?limit={limit}", limit));
    }

    public List<MovieSummary> searchByGenre(String genre, int limit) {
        return get(restClient.get().uri("/api/movies/search?genre={genre}&limit={limit}", genre, limit));
    }

    public List<MovieSummary> getByIds(List<Long> ids) {
        var joinedIds = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        // built via UriBuilder so the comma stays literal (ids=1,2,3) instead of being encoded as %2C
        return get(restClient.get().uri(uriBuilder -> uriBuilder
                .path("/api/movies/batch")
                .queryParam("ids", joinedIds)
                .build()));
    }

    private List<MovieSummary> get(RestClient.RequestHeadersSpec<?> request) {
        return Optional.ofNullable(request.retrieve().body(MOVIE_LIST))
                .orElseGet(List::of);
    }
}

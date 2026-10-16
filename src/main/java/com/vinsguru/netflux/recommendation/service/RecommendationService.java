package com.vinsguru.netflux.recommendation.service;

import com.vinsguru.netflux.recommendation.client.MovieClient;
import com.vinsguru.netflux.recommendation.client.UserClient;
import com.vinsguru.netflux.recommendation.config.MovieServiceProperties;
import com.vinsguru.netflux.recommendation.dto.HomeResponse;
import com.vinsguru.netflux.recommendation.dto.MovieSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private static final int LIMIT = 20;

    private final MovieClient movieClient;
    private final UserClient userClient;
    private final MovieServiceProperties movieServiceProperties;

    public RecommendationService(MovieClient movieClient,
                                 UserClient userClient,
                                 MovieServiceProperties movieServiceProperties) {
        this.movieClient = movieClient;
        this.userClient = userClient;
        this.movieServiceProperties = movieServiceProperties;
    }

    public HomeResponse getHome(Long userId) {
        // local variables keep the upstream call order: watchlist -> batch -> popular -> latest -> genres
        var watchlist = getWatchlist(userId);
        var popular = movieClient.getPopular(LIMIT);
        var latest = movieClient.getLatest(LIMIT);
        var byGenre = getByGenre();
        return new HomeResponse(
                watchlist,
                popular,
                latest,
                byGenre
        );
    }

    private List<MovieSummary> getWatchlist(Long userId) {
        var ids = userClient.getWatchlist(userId);
        return ids.isEmpty() ? List.of() : movieClient.getByIds(ids);
    }

    private Map<String, List<MovieSummary>> getByGenre() {
        return movieServiceProperties.genres().stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        genre -> movieClient.searchByGenre(genre, LIMIT),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }
}

package com.netzero.weather.service;

import com.netzero.store.domain.Store;
import com.netzero.store.repository.StoreRepository;
import com.netzero.weather.domain.WeatherForecast;
import com.netzero.weather.dto.WeatherSnapshot;
import com.netzero.weather.port.KmaForecastPort;
import com.netzero.weather.port.KmaResponse;
import com.netzero.weather.repository.WeatherForecastRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 기상청 단기예보를 조회·파싱하여 WeatherForecast 엔티티로 저장하고 WeatherSnapshot 으로 반환한다.
 * external.kma.enabled=true 일 때만 빈으로 등록된다.
 */
@Service
@ConditionalOnProperty(name = "external.kma.enabled", havingValue = "true")
public class WeatherService {

    private static final DateTimeFormatter KMA_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KmaForecastPort port;
    private final WeatherForecastRepository repo;
    private final StoreRepository stores;

    @Value("${external.kma.service-key:}")
    private String serviceKey;

    public WeatherService(KmaForecastPort port, WeatherForecastRepository repo, StoreRepository stores) {
        this.port = port;
        this.repo = repo;
        this.stores = stores;
    }

    /**
     * storeId 에 해당하는 매장의 nx/ny 좌표로 KMA API 를 호출하고
     * avgTemp = (TMX + TMN) / 2 를 계산한 뒤 WeatherForecast 를 저장한다.
     *
     * @return WeatherSnapshot (forecastDate, avgTemp, precipitationMm, precipitationProb, skyCode)
     */
    @Transactional
    public WeatherSnapshot fetchAndStore(Long storeId, LocalDate date) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        int nx = store.getNx() != null ? store.getNx() : 60;
        int ny = store.getNy() != null ? store.getNy() : 127;

        // KMA API 는 전날 23시 발표 기준으로 다음날 예보를 제공한다.
        String baseDate = date.minusDays(1).format(KMA_DATE);
        String fcstDate = date.format(KMA_DATE);

        Map<String, String> params = new HashMap<>();
        params.put("serviceKey", serviceKey);
        params.put("numOfRows", "1000");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("base_date", baseDate);
        params.put("base_time", "2300");
        params.put("nx", String.valueOf(nx));
        params.put("ny", String.valueOf(ny));

        KmaResponse response = port.getVillageForecast(params);
        List<KmaResponse.KmaItem> items = response.response().body().items().item();

        // 대상 날짜 항목만 필터
        List<KmaResponse.KmaItem> dayItems = items.stream()
                .filter(i -> fcstDate.equals(i.fcstDate()))
                .collect(Collectors.toList());

        BigDecimal tempMax = extractFirstDecimal(dayItems, "TMX");
        BigDecimal tempMin = extractFirstDecimal(dayItems, "TMN");
        BigDecimal avgTemp = computeAvgTemp(tempMax, tempMin);
        BigDecimal precipMm = extractPrecipSum(dayItems);
        Integer precipProb = extractMaxInt(dayItems, "POP");
        Integer skyCode = extractMaxInt(dayItems, "SKY");

        WeatherForecast wf = new WeatherForecast(
                store.getRegion(), date, tempMax, tempMin, avgTemp,
                precipMm, precipProb, skyCode, LocalDateTime.now());
        repo.save(wf);

        return new WeatherSnapshot(date, avgTemp, precipMm, precipProb, skyCode);
    }

    /**
     * start 부터 days 일 간의 예보를 모두 조회·저장하여 반환한다.
     */
    public List<WeatherSnapshot> coverageWeather(Long storeId, LocalDate start, int days) {
        return IntStream.range(0, days)
                .mapToObj(i -> fetchAndStore(storeId, start.plusDays(i)))
                .collect(Collectors.toList());
    }

    // ── 파싱 헬퍼 ─────────────────────────────────────────────────────────────

    private static BigDecimal computeAvgTemp(BigDecimal max, BigDecimal min) {
        if (max == null || min == null) return null;
        return max.add(min).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal extractFirstDecimal(List<KmaResponse.KmaItem> items, String category) {
        return items.stream()
                .filter(i -> category.equals(i.category()))
                .map(i -> parseDecimal(i.fcstValue()))
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    private static Integer extractMaxInt(List<KmaResponse.KmaItem> items, String category) {
        return items.stream()
                .filter(i -> category.equals(i.category()))
                .map(i -> parseIntSafe(i.fcstValue()))
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(null);
    }

    /** PCP 항목을 합산한다. "강수없음", "0mm", 빈값은 0으로 처리. */
    private static BigDecimal extractPrecipSum(List<KmaResponse.KmaItem> items) {
        return items.stream()
                .filter(i -> "PCP".equals(i.category()))
                .map(i -> parsePrecip(i.fcstValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal parsePrecip(String val) {
        if (val == null || val.isBlank() || "강수없음".equals(val)) return BigDecimal.ZERO;
        // "1.0mm", "50mm 이상" 등에서 숫자만 추출
        String num = val.replaceAll("[^0-9.]", "");
        return num.isEmpty() ? BigDecimal.ZERO : new BigDecimal(num);
    }

    private static BigDecimal parseDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try { return new BigDecimal(val.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Integer parseIntSafe(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}

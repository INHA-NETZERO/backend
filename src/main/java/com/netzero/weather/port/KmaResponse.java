package com.netzero.weather.port;

import java.util.List;

/**
 * KMA (기상청) 단기예보 JSON 응답 구조.
 * Reference: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst
 */
public record KmaResponse(KmaResponseBody response) {

    public record KmaResponseBody(KmaHeader header, KmaBody body) {}

    public record KmaHeader(String resultCode, String resultMsg) {}

    public record KmaBody(KmaItems items) {}

    public record KmaItems(List<KmaItem> item) {}

    /**
     * 예보 항목 하나.
     * category: TMX(최고기온), TMN(최저기온), SKY(하늘상태), POP(강수확률), PCP(강수량)
     */
    public record KmaItem(String category, String fcstDate, String fcstTime, String fcstValue) {}
}

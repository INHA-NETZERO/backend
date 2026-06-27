package com.netzero.forecast.dto;

import java.util.List;

public record SalesHistory(List<String> presignedUrls, String format) {}

package com.netzero.pipeline.controller;

import com.netzero.common.ApiResponse;
import com.netzero.pipeline.dto.PipelineResult;
import com.netzero.pipeline.service.DailyPipelineService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private final DailyPipelineService pipelineService;

    public PipelineController(DailyPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * POST /api/v1/pipeline/run?storeId={storeId}&date={date}
     * Triggers the daily pipeline for a specific store and date.
     */
    @PostMapping("/run")
    public ApiResponse<PipelineResult> run(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(pipelineService.run(storeId, date));
    }
}

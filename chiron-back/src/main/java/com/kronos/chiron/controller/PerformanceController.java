package com.kronos.chiron.controller;

import com.kronos.chiron.dto.ExercisePerformanceDto;
import com.kronos.chiron.dto.PerformanceRecordDto;
import com.kronos.chiron.dto.PerformanceSummaryDto;
import com.kronos.chiron.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping("/{username}")
    public ResponseEntity<PerformanceSummaryDto> getSummary(@PathVariable String username) {
        return ResponseEntity.ok(performanceService.getSummary(username));
    }

    @PostMapping("/{username}/record")
    public ResponseEntity<ExercisePerformanceDto> addRecord(
            @PathVariable String username,
            @RequestBody PerformanceRecordDto dto) {
        return ResponseEntity.ok(performanceService.addRecord(username, dto));
    }

    @PutMapping("/{username}/bodyweight")
    public ResponseEntity<PerformanceSummaryDto> updateBodyweight(
            @PathVariable String username,
            @RequestBody Map<String, Double> body) {
        Double poidsCorps = body.get("poidsCorps");
        if (poidsCorps == null || poidsCorps <= 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(performanceService.updateBodyweight(username, poidsCorps));
    }

    @GetMapping("/{username}/history/{exerciseType}")
    public ResponseEntity<List<ExercisePerformanceDto>> getHistory(
            @PathVariable String username,
            @PathVariable String exerciseType) {
        return ResponseEntity.ok(performanceService.getHistory(username, exerciseType));
    }
}

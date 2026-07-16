package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.RatingRequest;
import com.platform.service.RatingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/rating")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    public Result<?> submit(@RequestBody RatingRequest req, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        ratingService.submitRating(userId, req);
        return Result.ok();
    }

    @GetMapping("/user/{userId}")
    public Result<?> getUserRatings(@PathVariable Long userId) {
        return Result.ok(ratingService.getUserRatings(userId));
    }
}

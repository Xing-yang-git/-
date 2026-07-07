package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.ApproveRequest;
import com.platform.model.dto.BorrowRequestDTO;
import com.platform.model.dto.ReturnRequest;
import com.platform.service.BorrowService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/borrow")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @GetMapping("/{id}")
    public Result<?> getDetail(@PathVariable UUID id) {
        return Result.ok(borrowService.getDetail(id));
    }

    @PostMapping
    public Result<?> apply(@RequestBody BorrowRequestDTO req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(borrowService.apply(userId, req));
    }

    @PutMapping("/{id}/approve")
    public Result<?> approveReject(@PathVariable UUID id, @RequestBody ApproveRequest req,
                                   Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        borrowService.approveReject(userId, id, req);
        return Result.ok();
    }

    @GetMapping("/applications/my")
    public Result<?> myApplications(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(borrowService.getMyApplications(userId));
    }

    @GetMapping("/approvals/pending")
    public Result<?> pendingApprovals(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(borrowService.getPendingApprovals(userId));
    }

    @PutMapping("/{id}/return")
    public Result<?> confirmReturn(@PathVariable UUID id, @RequestBody ReturnRequest req,
                                   Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        borrowService.confirmReturn(userId, id, req);
        return Result.ok();
    }
}

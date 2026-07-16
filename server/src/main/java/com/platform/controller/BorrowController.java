package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.ApproveRequest;
import com.platform.model.dto.BorrowRequestDTO;
import com.platform.model.dto.ReturnRequest;
import com.platform.service.BorrowService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/borrow")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @GetMapping("/{id}")
    public Result<?> getDetail(@PathVariable Long id) {
        return Result.ok(borrowService.getDetail(id));
    }

    @PostMapping
    public Result<?> apply(@RequestBody BorrowRequestDTO req, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(borrowService.apply(userId, req));
    }

    @PutMapping("/{id}/approve")
    public Result<?> approveReject(@PathVariable Long id, @RequestBody ApproveRequest req,
                                   Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        borrowService.approveReject(userId, id, req);
        return Result.ok();
    }

    @GetMapping("/applications/my")
    public Result<?> myApplications(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(borrowService.getMyApplications(userId));
    }

    @GetMapping("/approvals/pending")
    public Result<?> pendingApprovals(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(borrowService.getPendingApprovals(userId));
    }

    @PutMapping("/{id}/return")
    public Result<?> confirmReturn(@PathVariable Long id, @RequestBody ReturnRequest req,
                                   Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        borrowService.confirmReturn(userId, id, req);
        return Result.ok();
    }
}

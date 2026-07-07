---
name: test-guarantee
description: Use when reviewing or enforcing test coverage and test quality standards. Covers unit, integration, and E2E testing with language-specific patterns for JUnit 5 + Mockito (Java), Vitest (Vue), and Jest (miniprogram). Defines tiered coverage targets, mock/stub principles, test naming conventions, and what NOT to test. Use when the user says "test coverage", "test quality", or when performing code quality review that includes test assessment.
---

# Test Guarantee

## Overview

Systematic methodology for ensuring code has adequate, high-quality test coverage that catches regressions without becoming a maintenance burden.

This skill is language-agnostic. The calling agent provides project context.

## Core Principles

1. Test behavior, not implementation.
2. Coverage is a floor, not a ceiling.
3. Slow tests become skipped tests. Unit tests under 100ms, integration under 2s.
4. One assertion per test conceptually.
5. Determinism is non-negotiable. No flaky tests.
6. Readable tests document behavior.

## Test Pyramid

```
       ┌──────┐
       │ E2E  │  Few: critical user journeys only
       │ ~10% │
      ┌┴──────┴┐
      │Integration│ Moderate: component interactions, API contracts
      │   ~30%   │
     ┌┴──────────┴┐
     │    Unit     │  Many: isolated business logic, edge cases
     │    ~60%     │
     └─────────────┘
```

## Coverage Targets

### Java / Spring Boot

| Component | Line Cov | Branch Cov | Reasoning |
|-----------|----------|------------|-----------|
| Service (business logic) | >= 85% | >= 80% | Core domain |
| Utility / Helper | >= 85% | >= 85% | Pure functions |
| Controller | 60-70% | N/A | Thin layer, use MockMvc |
| Repository (custom queries) | 40-50% | N/A | Custom queries only |
| Security (JWT, filters) | >= 70% | >= 70% | Security-critical |
| WebSocket handlers | 50-60% | N/A | Integration test preferred |
| Config | 10-20% | N/A | Bean wiring only |
| DTO / Entity / Enum | 0-10% | N/A | Pure data |

### Vue / TypeScript

| Component | Line Cov | Reasoning |
|-----------|----------|-----------|
| Composable (useXxx) | >= 85% | Reusable stateful logic |
| Complex View (forms, dashboards) | 70-80% | Interaction flows |
| Utility function | >= 85% | Pure functions |
| Simple View (display-only) | 30-50% | Snapshot + presence |
| Store (Pinia) | >= 70% | State mutations |
| API wrapper | 40-50% | Error handling |
| Router config | 10-20% | Guard redirects |

### Miniprogram / JavaScript

| Component | Line Cov | Reasoning |
|-----------|----------|-----------|
| Utility function (utils/) | >= 80% | Most testable part |
| Page JS logic | 30-40% | Extract to pure functions |
| Component JS | 20-30% | Props and events |
| WXML / WXSS | 0% | Manual review only |

## What to Test

### Required
- Business rules: every if branch
- Error paths: DB down, API timeout, invalid input
- Edge cases: null, empty, boundaries, zero
- Security logic: auth, roles, tokens
- Data transformations: DTO-Entity, form-API
- State machines: every transition

### Forbidden
- Getters and setters
- Framework behavior (Spring, Vue, JPA)
- Language features
- Third-party code internals
- Trivial delegation (Controller pass-through)
- Config values

## Mock Principles

Mock at the architectural boundary only:

```
Your Code                    External World
Service --calls--> Mock --> Repository
(test this)                  (mock this)
```

**Mock when:** database, external API, file system, clock, random generator.
**Don't mock:** value objects, pure functions, same-layer collaborators.

```java
// BAD: Mock train wreck
when(orderRepo.findById(any())).thenReturn(order);
when(order.getCustomer()).thenReturn(customer);
when(customer.getAddress()).thenReturn(address);

// GOOD: Complete stub
Customer c = Customer.builder().name("Alice")
    .address(Address.of("123 Main St")).build();
when(orderRepo.findById(1L)).thenReturn(
    Optional.of(Order.builder().customer(c).build()));
```

```java
// BAD: Verify internal interaction
verify(notificationService, times(1)).sendEmail(any());

// GOOD: Assert observable outcome
assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
```

## Test Quality Standards

### Naming: should_expected_when_condition

```
should_returnOrder_when_validIdProvided
should_throwNotFound_when_orderDoesNotExist
```

### AAA Pattern (Arrange, Act, Assert)

```java
@Test
void should_returnRefund_when_validOrder() {
    // Arrange
    Order order = OrderFixture.deliveredOrder();
    when(orderRepo.findById(1L)).thenReturn(Optional.of(order));

    // Act
    Refund result = refundService.processRefund(1L);

    // Assert
    assertThat(result.getStatus()).isEqualTo(RefundStatus.PENDING);
}
```

### Flaky Test Causes and Fixes

| Cause | Fix |
|-------|-----|
| Instant.now() / new Date() | Inject Clock |
| Math.random() | Seed generator |
| Static fields / singleton cache | Reset in @BeforeEach |
| Real HTTP to external API | WireMock / MSW |
| Shared DB across parallel runners | Per-test data or rollback |
| Thread.sleep(100) for async | Awaitility or CompletableFuture |

## Language Patterns

### Java: JUnit 5 + Mockito

```java
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {
    @Mock private OrderRepository orderRepo;
    @Mock private PaymentGateway paymentGateway;
    @InjectMocks private RefundService refundService;

    @Test
    void should_returnRefund_when_validOrder() {
        when(orderRepo.findById(1L)).thenReturn(
            Optional.of(OrderFixture.deliveredOrder()));
        Refund result = refundService.processRefund(1L,
            RefundReason.CUSTOMER_REQUEST);
        assertThat(result.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void should_throwNotFound_when_orderDoesNotExist() {
        when(orderRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class,
            () -> refundService.processRefund(99L, ...));
    }
}
```

Tools: AssertJ assertions, Mockito mocking, MockMvc for controllers, @DataJpaTest with H2 for repositories. Use slices over @SpringBootTest.

### Vue: Vitest + @vue/test-utils

```typescript
import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'

describe('AuditView', () => {
  it('renders pending items', async () => {
    const mockApi = { getPendingAudits: vi.fn()
        .mockResolvedValue([{ id: 1, title: 'Item', status: 'pending' }]) }
    const wrapper = mount(AuditView, {
      global: { provide: { api: mockApi } }
    })
    await flushPromises()
    expect(wrapper.find('.audit-row').exists()).toBe(true)
  })
})
```

Tools: Vitest runner, @vue/test-utils mount, MSW for HTTP mocking. Use flushPromises() not setTimeout.

### Miniprogram: Jest

```javascript
// utils/validator.js
export function validatePublishForm(data) {
  const errors = []
  if (!data.title || !data.title.trim()) errors.push('title required')
  if (data.title && data.title.length > 50) errors.push('title too long')
  if (!data.price || data.price <= 0) errors.push('price invalid')
  return { valid: errors.length === 0, errors }
}

// utils/__tests__/validator.test.js
import { validatePublishForm } from '../validator'

describe('validatePublishForm', () => {
  it('passes with valid data', () => {
    expect(validatePublishForm({ title: 'Book', price: 25 }).valid).toBe(true)
  })
  it('fails with empty title', () => {
    expect(validatePublishForm({ title: '', price: 25 }).valid).toBe(false)
  })
})
```

## Test File Organization

```
server/src/test/java/com/platform/service/RefundServiceTest.java
admin/src/__tests__/views/AuditView.test.ts
miniprogram/utils/__tests__/validator.test.js
```

Tests mirror the production tree exactly.

## Review Procedure

### Step 1: Coverage numbers
Run coverage tool (JaCoCo / Vitest --coverage / Jest --coverage), map to tier targets.

### Step 2: Quality spot-check (5 files)
1. Read 3 test methods: follow AAA? names clear?
2. Check asserts: behavior or implementation?
3. Scan for mock abuse: train wrecks? verify() instead of assertThat()?
4. Check edge coverage: null? exceptions? boundaries?
5. Missing tests: production if-branches without tests?

### Step 3: Report

| File | Line Cov | Target | Status |
|------|----------|--------|--------|
| RefundService.java | 92% | >=85% | OK |
| AuditView.vue | 35% | 70-80% | LOW |

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Testing private methods | Test through public API; extract if too complex |
| Mocking class under test | Use @InjectMocks, not Spy |
| Thread.sleep(500) | Awaitility or CompletableFuture |
| Hardcoded dates | Inject Clock |
| No assertion in test | Every test needs explicit assertThat |
| Testing the mock | Assert your code's output, not mock's return value |
| Giant @BeforeEach | Use test fixtures |
| Coverage-driven testing | Lines must have assertions that CAN fail |

## Bottom Line

Test coverage is a confidence metric, not a compliance metric. 95% coverage with no assertions = zero confidence. 50% with precise assertions on critical paths = high confidence. The goal is to refactor fearlessly.

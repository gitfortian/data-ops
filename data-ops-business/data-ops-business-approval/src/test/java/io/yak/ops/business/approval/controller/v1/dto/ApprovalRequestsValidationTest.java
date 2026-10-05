package io.yak.ops.business.approval.controller.v1.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.FlowPageQueryDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.MineQueryDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.PageQueryDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ApprovalRequestsValidationTest {

  @Test
  void allPagedQueriesRejectNonPositiveAndUnboundedSizes() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      PageQueryDTO todo = new PageQueryDTO();
      todo.setPageNo(0);
      todo.setPageSize(201);
      MineQueryDTO mine = new MineQueryDTO();
      mine.setPageSize(0);
      FlowPageQueryDTO flows = new FlowPageQueryDTO();
      flows.setPageNo(-1);

      assertInvalid(validator, todo, Set.of("pageNo", "pageSize"));
      assertInvalid(validator, mine, Set.of("pageSize"));
      assertInvalid(validator, flows, Set.of("pageNo"));
    }
  }

  private static void assertInvalid(Validator validator, Object query, Set<String> fields) {
    Set<String> actual = validator.validate(query).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
    assertTrue(actual.containsAll(fields), "expected validation errors for " + fields + ", got " + actual);
  }
}

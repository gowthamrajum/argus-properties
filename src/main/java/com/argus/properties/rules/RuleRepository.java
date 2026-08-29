package com.argus.properties.rules;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleRepository extends JpaRepository<Rule, Long> {

  Optional<Rule> findByShapeIdAndCode(String shapeId, String code);

  boolean existsByShapeIdAndCode(String shapeId, String code);

  List<Rule> findByShapeIdOrderByCodeAsc(String shapeId);

  /**
   * One query for every combination of filters, with null meaning "do not filter".
   *
   * <p>Ordered by severity first so the listing is useful without a client sorting it - HIGH before
   * MEDIUM before LOW, which is the order anyone triaging wants and is not the enum's alphabetical
   * order.
   */
  @Query("""
      select r from Rule r
      where (:shapeId is null or r.shapeId = :shapeId)
        and (:kind is null or r.kind = :kind)
        and (:severity is null or r.severity = :severity)
        and (:enabled is null or r.enabled = :enabled)
      order by case r.severity when 'HIGH' then 0 when 'MEDIUM' then 1 else 2 end,
               r.shapeId asc, r.code asc
      """)
  List<Rule> search(@Param("shapeId") String shapeId,
                    @Param("kind") RuleKind kind,
                    @Param("severity") Severity severity,
                    @Param("enabled") Boolean enabled);
}

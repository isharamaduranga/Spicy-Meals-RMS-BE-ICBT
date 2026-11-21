package com.ceyentra.sm.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.ceyentra.sm.repository.FinanceRepository.*;

@Repository
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public Map<String, Object> operations(Instant asOf) {
        LocalDate today = asOf.atOffset(REPORT_OFFSET).toLocalDate();
        LocalDate month = today.withDayOfMonth(1);
        LocalDate firstDay = today.minusDays(6);
        MapSqlParameterSource params = new MapSqlParameterSource("weekStart", utcBoundary(firstDay))
                .addValue("tomorrow", utcBoundary(today.plusDays(1)))
                .addValue("monthStart", utcBoundary(month))
                .addValue("previousStart", utcBoundary(month.minusMonths(1)));

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("asOf", asOf.toString());
        dashboard.put("totals", jdbc.queryForMap("SELECT"
                + " (SELECT COUNT(*) FROM user WHERE user_role='CUSTOMER') AS customers,"
                + " (SELECT COUNT(*) FROM meal_order) AS orders,"
                + " (SELECT COUNT(*) FROM tables_reservation) AS reservations,"
                + " " + INCOME + " AS income FROM payment p", params));
        dashboard.put("thisMonth", jdbc.queryForMap("SELECT " + INCOME + " AS income, " + OUTSTANDING
                + " AS outstanding FROM payment p WHERE created_date >= :monthStart AND created_date < :tomorrow", params));
        dashboard.put("previousMonthIncome", jdbc.queryForObject("SELECT " + INCOME
                + " FROM payment p WHERE created_date >= :previousStart AND created_date < :monthStart", params, BigDecimal.class));

        String day = "DATE_FORMAT(DATE_ADD(created_date, INTERVAL 330 MINUTE),'%Y-%m-%d')";
        String range = "created_date >= :weekStart AND created_date < :tomorrow GROUP BY day";
        String sql = "SELECT day, SUM(customers) AS customers, SUM(orders) AS orders FROM ("
                + "SELECT " + day + " AS day, COUNT(*) AS customers, 0 AS orders FROM user WHERE user_role='CUSTOMER' AND " + range
                + " UNION ALL SELECT " + day + " AS day, 0, COUNT(*) FROM meal_order WHERE " + range
                + ") activity GROUP BY day";
        Map<String, Map<String, Object>> activity = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, params)) {
            activity.put((String) row.get("day"), row);
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate date = firstDay; !date.isAfter(today); date = date.plusDays(1)) {
            Map<String, Object> row = activity.get(date.toString());
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("day", date.toString());
                row.put("customers", 0);
                row.put("orders", 0);
            }
            days.add(row);
        }
        dashboard.put("days", days);
        return dashboard;
    }

    public Optional<Map<String, Object>> customer(String email) {
        MapSqlParameterSource params = new MapSqlParameterSource("email", email);
        List<Long> ids = jdbc.queryForList("SELECT id FROM user WHERE email=:email AND user_role='CUSTOMER'",
                params, Long.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        params.addValue("id", ids.get(0));
        Map<String, Object> result = jdbc.queryForMap("SELECT"
                + " (SELECT COUNT(*) FROM meal_order WHERE customer_id=:id) AS orders,"
                + " (SELECT COUNT(*) FROM tables_reservation WHERE customer_id=:id) AS reservations", params);
        result.put("paid", jdbc.queryForObject("SELECT " + INCOME + " FROM payment p"
                + " LEFT JOIN meal_order o ON o.id=p.meal_order_id"
                + " LEFT JOIN tables_reservation r ON r.id=p.reservation_id"
                + " WHERE COALESCE(o.customer_id,r.customer_id)=:id", params, BigDecimal.class));
        List<Map<String, Object>> visits = jdbc.queryForList("SELECT b.name AS restaurant, r.reservation_code AS reference,"
                + " DATE_FORMAT(r.reserved_date,'%Y-%m-%dT%H:%i:%sZ') AS reservedAt, r.max_count AS guests"
                + " FROM tables_reservation r LEFT JOIN restaurant b ON b.id=r.restaurant_id"
                + " WHERE r.customer_id=:id AND r.status='ACTIVE' AND r.operational_status='APPROVED'"
                + " AND r.reserved_date >= UTC_TIMESTAMP() ORDER BY r.reserved_date, r.id LIMIT 1", params);
        result.put("nextVisit", visits.isEmpty() ? null : visits.get(0));
        return Optional.of(result);
    }
}

package com.ceyentra.sm.repository;

import com.ceyentra.sm.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FinanceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final int PAGE_SIZE = 20;
    private static final String AMOUNT = "CAST(p.price AS DECIMAL(18,2))";
    private static final String JOINS = " FROM payment p"
            + " LEFT JOIN meal_order o ON o.id = p.meal_order_id"
            + " LEFT JOIN tables_reservation r ON r.id = p.reservation_id"
            + " LEFT JOIN user u ON u.id = COALESCE(o.customer_id, r.customer_id)"
            + " LEFT JOIN restaurant b ON b.id = COALESCE(o.restaurant_id, r.restaurant_id)";
    private static final String FIELDS = "SELECT p.id, p.payment_status AS status, p.query_type AS type, "
            + AMOUNT + " AS amount, DATE_FORMAT(p.created_date, '%Y-%m-%dT%H:%i:%sZ') AS createdAt,"
            + " COALESCE(o.order_code, r.reservation_code) AS reference,"
            + " COALESCE(NULLIF(o.full_name,''), u.name) AS customer, b.name AS restaurant";
    static final String INCOME = "COALESCE(SUM(CASE WHEN p.payment_status = 'PAID' THEN "
            + AMOUNT + " ELSE 0 END),0)";
    static final String OUTSTANDING = "COALESCE(SUM(CASE WHEN p.payment_status IN ('PENDING','NOT_PAID') THEN "
            + AMOUNT + " ELSE 0 END),0)";

    public Map<String, Object> payments(int page, PaymentStatus status, String search) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = " WHERE 1=1";
        if (status != null) {
            where += " AND p.payment_status = :status";
            params.addValue("status", status.name());
        }
        if (!search.isEmpty()) {
            where += " AND (CAST(p.id AS CHAR) = :search OR LOCATE(:search, COALESCE(o.order_code,r.reservation_code)) > 0"
                    + " OR LOCATE(:search, COALESCE(NULLIF(o.full_name,''),u.name)) > 0 OR LOCATE(:search,b.name) > 0)";
            params.addValue("search", search);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", jdbc.queryForMap("SELECT COUNT(*) AS transactions, " + INCOME + " AS income, "
                + OUTSTANDING + " AS outstanding FROM payment p", params));
        result.put("total", jdbc.queryForObject("SELECT COUNT(*)" + JOINS + where, params, Long.class));
        params.addValue("offset", (long) page * PAGE_SIZE);
        result.put("pageSize", PAGE_SIZE);
        result.put("rows", jdbc.queryForList(FIELDS + JOINS + where
                + " ORDER BY p.created_date DESC, p.id DESC LIMIT " + PAGE_SIZE + " OFFSET :offset", params));
        return result;
    }

    public Optional<Map<String, Object>> payment(long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        String details = ", o.id AS orderId, r.id AS reservationId, u.email,"
                + " COALESCE(NULLIF(o.mobile_number,''), u.phone_number) AS phone, o.delivery_address AS address,"
                + " COALESCE(o.operational_status,r.operational_status) AS orderStatus,"
                + " COALESCE(o.meal_order_type,r.table_reservation_type) AS bookingType,"
                + " DATE_FORMAT(r.reserved_date,'%Y-%m-%dT%H:%i:%sZ') AS reservedAt, r.max_count AS guests";
        List<Map<String, Object>> rows = jdbc.queryForList(FIELDS + details + JOINS + " WHERE p.id = :id", params);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> payment = rows.get(0);
        if (payment.get("orderId") != null) {
            params.addValue("orderId", payment.get("orderId"));
            payment.put("items", jdbc.queryForList("SELECT m.name, d.qty, CAST(d.price AS DECIMAL(18,2)) AS unitPrice,"
                    + " CAST(d.discount AS DECIMAL(18,2)) AS discount, CAST(d.price*d.qty AS DECIMAL(18,2)) AS lineTotal"
                    + " FROM meal_order_detail d LEFT JOIN meal m ON m.id=d.meal_id"
                    + " WHERE d.meal_order_id=:orderId ORDER BY d.id", params));
        } else if (payment.get("reservationId") != null) {
            params.addValue("reservationId", payment.get("reservationId"));
            payment.put("tables", jdbc.queryForList("SELECT t.table_code AS code, t.category, t.seat_limit AS seats"
                    + " FROM tables_reservation_detail d LEFT JOIN tables t ON t.id=d.table_id"
                    + " WHERE d.reservation_id=:reservationId ORDER BY d.id", params));
        }
        return Optional.of(payment);
    }
}

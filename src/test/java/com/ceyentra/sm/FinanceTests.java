package com.ceyentra.sm;

import com.ceyentra.sm.repository.FinanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
class FinanceTests {
    @Autowired FinanceRepository finance;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    @Transactional
    @SuppressWarnings("unchecked")
    void leapMonthAndYearUseLocalMidnightAndPaidAmountsOnly() {
        // Session-local tables keep report fixtures isolated from the restaurant's data.
        jdbc.execute("CREATE TEMPORARY TABLE payment (created_date DATETIME, price FLOAT, payment_status VARCHAR(16))");
        try {
            jdbc.update("INSERT INTO payment VALUES ('2096-01-31 18:29:59',9,'PAID'),"
                    + " ('2096-01-31 18:30:00',12.35,'PAID'), ('2096-02-29 18:29:59',8,'PENDING'),"
                    + " ('2096-02-29 18:29:59',5,'NOT_PAID'), ('2096-02-29 18:30:00',7,'PAID')");
            LocalDate february = LocalDate.of(2096, 2, 1);
            Map<String, Object> month = finance.report(february, false);
            List<Map<String, Object>> days = (List<Map<String, Object>>) month.get("rows");
            assertEquals(29, days.size());
            assertEquals("2096-02-29", days.get(28).get("period"));
            assertMetric(days.get(0), "income", "12.35");
            assertMetric(days.get(1), "payments", "0");
            assertMetric(days.get(28), "income", "0");
            assertMetric(days.get(28), "outstanding", "13.00");
            Map<String, Object> totals = (Map<String, Object>) month.get("totals");
            assertMetric(totals, "income", "12.35");
            assertMetric(totals, "payments", "3");
            List<Map<String, Object>> months = (List<Map<String, Object>>) finance.report(february.withMonth(1), true).get("rows");
            assertEquals(12, months.size());
            assertMetric(months.get(0), "income", "9");
            assertMetric(months.get(1), "outstanding", "13");
            assertMetric(months.get(2), "income", "7");
        } finally {
            jdbc.execute("DROP TEMPORARY TABLE payment");
        }
    }

    private void assertMetric(Map<String, Object> row, String key, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(row.get(key).toString())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void existingPaymentsCanBeFoundAndOpenedWithTheirRecordedAmounts() throws Exception {
        List<Map<String, Object>> records = jdbc.queryForList("SELECT id, price, payment_status,"
                + " meal_order_id, reservation_id FROM payment ORDER BY id DESC LIMIT 5");
        for (Map<String, Object> record : records) {
            String id = record.get("id").toString();
            mvc.perform(get("/v1/admin/payments").param("search", id))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows").isNotEmpty());
            ResultActions details = mvc.perform(get("/v1/admin/payments/" + id))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(record.get("id")))
                    .andExpect(jsonPath("$.data.status").value(record.get("payment_status")));
            if (record.get("price") != null) {
                double amount = new BigDecimal(record.get("price").toString())
                        .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
                details.andExpect(jsonPath("$.data.amount").value(amount));
            }
            if (record.get("meal_order_id") != null) {
                details.andExpect(jsonPath("$.data.items").isArray());
            } else if (record.get("reservation_id") != null) {
                details.andExpect(jsonPath("$.data.tables").isArray());
            }
        }
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanReadPaymentsAndOnlyAdminsCanReadReports() throws Exception {
        mvc.perform(get("/v1/admin/payments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.income").isNumber())
                .andExpect(jsonPath("$.data.rows").isArray());
        mvc.perform(get("/v1/admin/reports?year=2026")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsValidatePeriodAndPaymentsHandleMissingIds() throws Exception {
        mvc.perform(get("/v1/admin/reports?year=2026&month=11")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(30));
        mvc.perform(get("/v1/admin/reports?year=2026&month=13")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/payments/-1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customersCannotReadFinancialRecords() throws Exception {
        mvc.perform(get("/v1/admin/payments/1")).andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/reports?year=2026")).andExpect(status().isForbidden());
    }
}

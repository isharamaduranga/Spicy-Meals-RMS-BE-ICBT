package com.ceyentra.sm;

import com.ceyentra.sm.repository.DashboardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
class DashboardTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardRepository dashboard;

    @Test
    void rolesControlAccessToEachView() throws Exception {
        mvc.perform(get("/v1/dashboard/operations")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "STAFF"}) {
            mvc.perform(get("/v1/dashboard/operations").with(user("operator").roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.days.length()").value(7))
                    .andExpect(jsonPath("$.data.totals.income").isNumber());
        }
        mvc.perform(get("/v1/dashboard/operations").with(user("diner").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/dashboard/me").with(user("operator").roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @SuppressWarnings("unchecked")
    void activityFollowsLocalDatesAndCustomersSeeOnlyTheirOwnRecords() throws Exception {
        try {
            jdbc.execute("CREATE TEMPORARY TABLE user (id INT, email VARCHAR(80), user_role VARCHAR(20), created_date DATETIME)");
            jdbc.execute("CREATE TEMPORARY TABLE meal_order (id INT, customer_id INT, created_date DATETIME)");
            jdbc.execute("CREATE TEMPORARY TABLE tables_reservation (id INT, customer_id INT, restaurant_id INT,"
                    + " reservation_code VARCHAR(30), reserved_date DATETIME, operational_status VARCHAR(24), status VARCHAR(10), max_count INT)");
            jdbc.execute("CREATE TEMPORARY TABLE payment (meal_order_id INT, reservation_id INT, payment_status VARCHAR(12), price FLOAT, created_date DATETIME)");
            jdbc.update("INSERT INTO user VALUES (1,'guest@sample.test','CUSTOMER','2026-10-31 18:30:00'),"
                    + "(2,'other@sample.test','CUSTOMER','2026-10-20 12:00:00')");
            jdbc.update("INSERT INTO meal_order VALUES (10,1,'2026-10-31 18:30:00'),(20,2,'2026-10-31 18:29:59')");
            jdbc.update("INSERT INTO tables_reservation VALUES (1,1,NULL,'MY-VISIT',UTC_TIMESTAMP()+INTERVAL 2 DAY,'APPROVED','ACTIVE',3),"
                    + "(2,2,NULL,'OTHER',UTC_TIMESTAMP()+INTERVAL 1 DAY,'APPROVED','ACTIVE',4),"
                    + "(3,1,NULL,'DECLINED',UTC_TIMESTAMP()+INTERVAL 1 HOUR,'DECLINED','ACTIVE',1)");
            jdbc.update("INSERT INTO payment VALUES (10,NULL,'PAID',120.25,'2026-10-31 18:30:00'),"
                    + "(20,NULL,'PAID',50,'2026-10-31 18:29:59'),(NULL,1,'PAID',10,'2026-10-31 18:30:01'),"
                    + "(10,NULL,'PENDING',15,'2026-10-31 18:30:02')");
            Map<String, Object> board = dashboard.operations(Instant.parse("2026-10-31T20:00:00Z"));
            Map<String, Object> month = (Map<String, Object>) board.get("thisMonth");
            assertEquals(130.25, ((Number) month.get("income")).doubleValue());
            assertEquals(15.0, ((Number) month.get("outstanding")).doubleValue());
            assertEquals(50.0, ((Number) board.get("previousMonthIncome")).doubleValue());
            List<Map<String, Object>> days = (List<Map<String, Object>>) board.get("days");
            assertEquals("2026-11-01", days.get(6).get("day"));
            assertEquals(1, ((Number) days.get(6).get("customers")).intValue());
            assertEquals(1, ((Number) days.get(5).get("orders")).intValue());
            assertEquals(0, ((Number) days.get(0).get("orders")).intValue());

            mvc.perform(get("/v1/dashboard/me").param("customerId","2").with(user("guest@sample.test").roles("CUSTOMER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.orders").value(1))
                    .andExpect(jsonPath("$.data.reservations").value(2)).andExpect(jsonPath("$.data.paid").value(130.25))
                    .andExpect(jsonPath("$.data.nextVisit.reference").value("MY-VISIT"));
        } finally {
            jdbc.execute("DROP TEMPORARY TABLE IF EXISTS payment, tables_reservation, meal_order, user");
        }
    }
}

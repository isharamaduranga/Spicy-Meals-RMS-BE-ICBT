package com.ceyentra.sm.constant;

import com.ceyentra.sm.entity.MealOrderEntity;
import com.ceyentra.sm.entity.TableReservationEntity;
import lombok.Getter;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

@Getter
public final class DecisionEmail {
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter
            .ofPattern("d MMM yyyy 'at' h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Colombo"));

    private final String subject;
    private final String html;

    public static DecisionEmail forOrder(MealOrderEntity order, boolean approved, String note) {
        String name = order.getFullName();
        if (!StringUtils.hasText(name) && order.getUserEntity() != null) {
            name = order.getUserEntity().getName();
        }
        String details = row("Order reference", order.getOrderId())
                + row("Restaurant", order.getRestaurant() == null ? null : order.getRestaurant().getName())
                + row("Ordered on", localTime(order.getCreatedDate()))
                + row("Delivery address", order.getDeliveryAddress());
        return new DecisionEmail("Meal order", order.getId(), approved, name,
                approved ? "The restaurant has accepted your meal order. You can follow its status in My Orders."
                        : "The restaurant has declined your meal order. You can find the order details in My Orders.",
                details, note);
    }

    public static DecisionEmail forReservation(TableReservationEntity reservation, boolean approved) {
        String details = row("Reservation reference", reservation.getReservationCode())
                + row("Restaurant", reservation.getRestaurant() == null ? null : reservation.getRestaurant().getName())
                + row(approved ? "Reserved for" : "Requested for", localTime(reservation.getReservedDate()))
                + row("Guests", reservation.getMax_count());
        return new DecisionEmail("Table reservation", reservation.getId(), approved,
                reservation.getCustomer() == null ? null : reservation.getCustomer().getName(),
                approved ? "Your table is confirmed. Please keep your reservation reference for your visit."
                        : "The restaurant could not confirm your table for the requested time. You can request another time in your account.",
                details, reservation.getApprovedNote());
    }

    private DecisionEmail(String kind, Long id, boolean approved, String name, String message, String details, String note) {
        String decision = approved ? "approved" : "declined";
        subject = kind + (id == null ? "" : " #" + id) + " " + decision + " | Spicy Meals";
        String greeting = StringUtils.hasText(name) ? "Hello " + escape(name) + "," : "Hello,";
        String noteBlock = StringUtils.hasText(note)
                ? "<p style='margin:24px 0 4px'><strong>Note from the restaurant</strong></p><p style='margin:0'>"
                + escape(note).replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>") + "</p>" : "";
        html = "<!doctype html><html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'></head>"
                + "<body style='margin:0;background:#faf6f0;color:#30261e;font:16px/1.5 Arial,sans-serif'>"
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:24px 12px'>"
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='max-width:560px;background:#fff;text-align:left'>"
                + "<tr><td style='background:#84380d;color:#fff;padding:16px 24px;font-size:18px'><strong>Spicy Meals</strong></td></tr>"
                + "<tr><td style='padding:24px'><h1 style='margin:0 0 18px;font-size:24px'>" + kind + " " + decision + "</h1>"
                + "<p>" + greeting + "</p><p>" + message + "</p>"
                + "<table cellpadding='0' cellspacing='0' width='100%'>" + details + "</table>"
                + noteBlock + "<p style='margin:24px 0 0'>The Spicy Meals team</p></td></tr></table></td></tr></table></body></html>";
    }

    private static String row(String label, Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return "";
        }
        return "<tr><th scope='row' align='left' style='padding:8px 12px 8px 0;border-bottom:1px solid #eee'>"
                + label + "</th><td style='padding:8px 0;border-bottom:1px solid #eee;overflow-wrap:anywhere'>"
                + escape(value.toString()) + "</td></tr>";
    }

    private static String localTime(Date date) {
        return date == null ? null : LOCAL_TIME.format(date.toInstant()) + " (Sri Lanka time)";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value.trim(), "UTF-8");
    }
}

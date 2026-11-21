package com.ceyentra.sm;

import com.ceyentra.sm.constant.EmailTemplateConstant;
import com.ceyentra.sm.dto.UserDTO;
import com.ceyentra.sm.entity.MealOrderEntity;
import com.ceyentra.sm.entity.RestaurantEntity;
import com.ceyentra.sm.entity.TableReservationEntity;
import com.ceyentra.sm.entity.UserEntity;
import com.ceyentra.sm.enums.MealOperationalStatus;
import com.ceyentra.sm.enums.TableReservationOperationalStatus;
import com.ceyentra.sm.service.impl.EmailServiceImpl;
import com.ceyentra.sm.util.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionEmailTests {
    private final JavaMailSender transport = mock(JavaMailSender.class);
    private final UserDTO recipient = UserDTO.builder().email("customer@example.test").build();
    private final RestaurantEntity branch = RestaurantEntity.builder().name("Kandy & Co").build();
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transport.createMimeMessage()).thenAnswer(invocation -> new JavaMailSenderImpl().createMimeMessage());
        EmailSender sender = new EmailSender(transport);
        ReflectionTestUtils.setField(sender, "mailFrom", "restaurant@example.test");
        service = new EmailServiceImpl(sender, new EmailTemplateConstant());
    }

    @ParameterizedTest
    @EnumSource(value = MealOperationalStatus.class, names = {"ACCEPTED", "DECLINED"})
    void orderDecisionReachesTheHtmlPart(MealOperationalStatus status) throws Exception {
        MealOrderEntity order = MealOrderEntity.builder().id(14L).orderId("M/124").fullName("Nila & Sam")
                .restaurant(branch).deliveryAddress("Lake Road").build();
        service.sendUserMealOrderOperationalStatusChangeEmail(recipient, order, status, "Kitchen <note>\nSecond line");
        String outcome = status == MealOperationalStatus.ACCEPTED ? "approved" : "declined";
        String html = deliveredHtml("Meal order #14 " + outcome + " | Spicy Meals");
        assertTrue(html.contains("Meal order " + outcome));
        assertTrue(html.contains("Hello Nila &amp; Sam,"));
        assertTrue(html.contains("M/124") && html.contains("Kandy &amp; Co") && html.contains("Lake Road"));
        assertTrue(html.contains("Kitchen &lt;note&gt;<br>Second line"));
    }

    @ParameterizedTest
    @EnumSource(value = TableReservationOperationalStatus.class, names = {"APPROVED", "DECLINED"})
    void reservationDecisionIncludesTheRequestedVisit(TableReservationOperationalStatus status) throws Exception {
        UserEntity customer = new UserEntity();
        customer.setName("Amal");
        TableReservationEntity reservation = TableReservationEntity.builder().id(27L).reservationCode("R/456")
                .customer(customer).restaurant(branch).max_count(4)
                .reservedDate(Date.from(Instant.parse("2026-11-21T19:00:00Z"))).build();
        service.sendUserTableReservationOperationalStatusChangeEmail(recipient, reservation, status);
        String outcome = status == TableReservationOperationalStatus.APPROVED ? "approved" : "declined";
        String html = deliveredHtml("Table reservation #27 " + outcome + " | Spicy Meals");
        assertTrue(html.contains("Table reservation " + outcome));
        assertTrue(html.contains("Hello Amal,") && html.contains("R/456"));
        assertTrue(html.contains("22 Nov 2026 at 12:30 AM (Sri Lanka time)"));
        assertTrue(html.contains("Guests") && html.contains(">4</td>"));
        assertFalse(html.contains("Note from the restaurant"));
    }

    @Test
    void newRequestsHaveNoDecisionEmail() throws Exception {
        service.sendUserMealOrderOperationalStatusChangeEmail(recipient, new MealOrderEntity(), MealOperationalStatus.NEW, null);
        service.sendUserTableReservationOperationalStatusChangeEmail(recipient, new TableReservationEntity(), TableReservationOperationalStatus.NEW);
        verifyNoInteractions(transport);
    }

    private String deliveredHtml(String expectedSubject) throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(transport).send(captor.capture());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        captor.getValue().writeTo(bytes);
        MimeMessage message = new MimeMessage(null, new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(recipient.getEmail(), message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(expectedSubject, message.getSubject());
        Multipart parts = (Multipart) message.getContent();
        assertTrue(parts.getBodyPart(0).isMimeType("text/html"));
        return (String) parts.getBodyPart(0).getContent();
    }
}

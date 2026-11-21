/**
 * @author :  Dinuth Dheeraka
 * Created : 8/11/2023 11:36 AM
 */
package com.ceyentra.sm.service.impl;

import com.ceyentra.sm.constant.EmailTemplateConstant;
import com.ceyentra.sm.constant.DecisionEmail;
import com.ceyentra.sm.dto.UserDTO;
import com.ceyentra.sm.entity.MealOrderEntity;
import com.ceyentra.sm.entity.TableReservationEntity;
import com.ceyentra.sm.enums.MealOperationalStatus;
import com.ceyentra.sm.enums.TableReservationOperationalStatus;
import com.ceyentra.sm.service.EmailService;
import com.ceyentra.sm.util.EmailSender;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.MessagingException;


@Service
@Transactional
@Log4j2
public class EmailServiceImpl implements EmailService {

    private final EmailSender emailSender;
    private final EmailTemplateConstant emailTemplateConstant;

    @Autowired
    public EmailServiceImpl(EmailSender emailSender, EmailTemplateConstant emailTemplateConstant) {
        this.emailSender = emailSender;
        this.emailTemplateConstant = emailTemplateConstant;
    }

    //done
    @Override
    public void sendUserOTPEmail(UserDTO userDTO, int OTP) throws MessagingException {
        log.info("Starting password reset OTP email delivery");
        try {

            //sending email that contains OTP
            emailSender.sendSimpleEmail(userDTO.getEmail(),
                    EmailTemplateConstant.SEND_USER_PASSWORD_RESET_OTP_EMAIL_SUBJECT,
                    emailTemplateConstant.sendUserOTPTemplate(OTP));

        } catch (Exception e) {
            log.error("function sendUserOTP {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void sendUserNewPasswordEmail(UserDTO userDTO) throws MessagingException {
        log.info("start sendUserNewPasswordEmail @Param userDTO : {}", userDTO);
        try {

            //sending email that contains OTP
            emailSender.sendSimpleEmail(userDTO.getEmail(),
                    EmailTemplateConstant.SEND_USER_RESET_PASSWORD_EMAIL_SUBJECT,
                    emailTemplateConstant.sendUserNewPasswordTemplate(userDTO.getPassword()));

        } catch (Exception e) {
            log.error("function sendUserOTP {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void sendUserMealOrderOperationalStatusChangeEmail(UserDTO user, MealOrderEntity order,
                                                              MealOperationalStatus status, String note) throws MessagingException {
        if (status == MealOperationalStatus.ACCEPTED || status == MealOperationalStatus.DECLINED) {
            DecisionEmail email = DecisionEmail.forOrder(order, status == MealOperationalStatus.ACCEPTED, note);
            emailSender.sendSimpleEmail(user.getEmail(), email.getSubject(), email.getHtml());
        }
    }

    @Override
    public void sendUserTableReservationOperationalStatusChangeEmail(UserDTO user, TableReservationEntity reservation,
                                                                     TableReservationOperationalStatus status) throws MessagingException {
        if (status == TableReservationOperationalStatus.APPROVED || status == TableReservationOperationalStatus.DECLINED) {
            DecisionEmail email = DecisionEmail.forReservation(reservation, status == TableReservationOperationalStatus.APPROVED);
            emailSender.sendSimpleEmail(user.getEmail(), email.getSubject(), email.getHtml());
        }
    }
}

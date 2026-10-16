package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

@Component
public class ReminderChannelFactoryImpl implements ReminderChannelFactory {

    private final EmailReminderChannel emailReminderChannel;
    private final SmsReminderChannel smsReminderChannel;

    public ReminderChannelFactoryImpl(EmailReminderChannel emailReminderChannel,
                                      @Qualifier("smsReminderChannel") SmsReminderChannel smsReminderChannel) {
        this.emailReminderChannel = emailReminderChannel;
        this.smsReminderChannel = smsReminderChannel;
    }

    @Override
    public ReminderChannel obtenerCanal(CanalNotificacion tipo) {
        return switch (tipo) {
            case EMAIL -> emailReminderChannel;
            case SMS -> smsReminderChannel;
        };
    }
}
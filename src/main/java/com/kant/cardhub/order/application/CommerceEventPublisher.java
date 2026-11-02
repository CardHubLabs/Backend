package com.kant.cardhub.order.application;

import com.kant.cardhub.order.application.port.CommerceEvent;
import com.kant.cardhub.order.application.port.CommerceEventPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CommerceEventPublisher {

    private final ObjectProvider<CommerceEventPort> portProvider;

    public CommerceEventPublisher(ObjectProvider<CommerceEventPort> portProvider) {
        this.portProvider = portProvider;
    }

    public void publish(CommerceEvent event) {
        CommerceEventPort port = portProvider.getIfAvailable();
        if (port != null) {
            port.publish(event);
        }
    }
}

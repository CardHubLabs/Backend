package com.kant.cardhub.order.application.port;

public interface CommerceEventPort {
    void publish(CommerceEvent event);
}

package com.kant.cardhub.worker.application;

public interface ScheduledJobHandler {

    String jobType();

    void handle(String payload) throws Exception;
}


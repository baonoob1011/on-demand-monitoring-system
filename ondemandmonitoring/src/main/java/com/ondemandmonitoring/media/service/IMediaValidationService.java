package com.ondemandmonitoring.media.service;

public interface IMediaValidationService {

    void processObjectCreated(String bucket, String key, String eventIdentity);
}

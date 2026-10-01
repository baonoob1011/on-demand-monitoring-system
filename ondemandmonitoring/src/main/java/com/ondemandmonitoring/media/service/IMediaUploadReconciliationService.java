package com.ondemandmonitoring.media.service;

/** Recovery of acknowledged uploads when storage notifications are delayed or lost. */
public interface IMediaUploadReconciliationService {
    void reconcileUploadedObjects();
}

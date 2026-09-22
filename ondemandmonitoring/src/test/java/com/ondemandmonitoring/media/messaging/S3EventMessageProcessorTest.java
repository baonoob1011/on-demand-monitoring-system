package com.ondemandmonitoring.media.messaging;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.media.service.IMediaValidationService;
import org.junit.jupiter.api.Test;

class S3EventMessageProcessorTest {
    private final IMediaValidationService validation = mock(IMediaValidationService.class);
    private final S3EventMessageProcessor processor = new S3EventMessageProcessor(new ObjectMapper(), validation);

    @Test
    void ignoresS3TestEvent() throws Exception {
        processor.process("{\"Service\":\"Amazon S3\",\"Event\":\"s3:TestEvent\"}");
        verifyNoInteractions(validation);
    }

    @Test
    void decodesKeyAndPassesObjectCreatedToValidator() throws Exception {
        processor.process("""
                {"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"bucket"},
                "object":{"key":"staging%2Fmission%2Fphoto+one.jpg","sequencer":"A12"}}}]}
                """);
        verify(validation).processObjectCreated("bucket", "staging/mission/photo one.jpg", "A12");
    }
}

package de.otto.config.core.aws;

import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.aws.event.ChangeEventParser;
import de.otto.config.core.registry.SourceRegistry;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsChangeEventListenerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private Context context;

    @Mock
    private SourceRegistry sourceRegistry;

    @Mock
    private ChangeEventParser eventParser;

    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789/test-queue";

    private AwsChangeEventListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getSourceRegistry()).thenReturn(sourceRegistry);
        listener = new AwsChangeEventListener(sqsClient, QUEUE_URL, context, eventParser);
    }

    @Test
    void shouldReturnEarlyWhenNoMessagesReceived() {
        // given
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(Collections.emptyList()).build());

        // when
        listener.pollAndRefresh();

        // then
        verifyNoInteractions(eventParser);
        verify(sqsClient, never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    void shouldParseDispatchAndDeleteSingleMessage() throws Exception {
        // given
        Message message = Message.builder()
                .messageId("msg-1")
                .receiptHandle("handle-1")
                .body("{\"source\":\"aws.ssm\",\"detail-type\":\"Parameter Store Change\"}")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        SourceChangeEvent event = mock(SourceChangeEvent.class);
        when(eventParser.parse(message.body())).thenReturn(event);
        when(sourceRegistry.getValues()).thenReturn(Collections.emptyList());

        // when
        listener.pollAndRefresh();

        // then
        verify(eventParser).parse(message.body());
        verify(sqsClient).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCallRefreshOnSourcesThatMatchTheEvent() throws Exception {
        // given
        Message message = Message.builder()
                .messageId("msg-1")
                .receiptHandle("handle-1")
                .body("{\"source\":\"aws.ssm\"}")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        SourceChangeEvent event = mock(SourceChangeEvent.class);
        when(eventParser.parse(message.body())).thenReturn(event);

        Source<Configuration<?>> matchingSource = mock(Source.class);
        Source<Configuration<?>> nonMatchingSource = mock(Source.class);
        when(matchingSource.onChanged(event)).thenReturn(true);
        when(nonMatchingSource.onChanged(event)).thenReturn(false);
        when(sourceRegistry.getValues()).thenReturn(List.of(matchingSource, nonMatchingSource));

        // when
        listener.pollAndRefresh();

        // then
        verify(matchingSource).refresh();
        verify(nonMatchingSource, never()).refresh();
    }

    @Test
    void shouldDeleteMessageEvenWhenParsingFails() throws Exception {
        // given
        Message message = Message.builder()
                .messageId("msg-1")
                .receiptHandle("handle-1")
                .body("not-valid-json")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(eventParser.parse(message.body())).thenThrow(new RuntimeException("parse error"));

        // when
        listener.pollAndRefresh();

        // then — poison-pill protection: message is still scheduled for deletion
        verify(sqsClient).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    void shouldProcessAllMessagesAndDeleteInOneBatch() throws Exception {
        // given
        Message message1 = Message.builder()
                .messageId("msg-1")
                .receiptHandle("handle-1")
                .body("{\"source\":\"aws.ssm\"}")
                .build();
        Message message2 = Message.builder()
                .messageId("msg-2")
                .receiptHandle("handle-2")
                .body("{\"source\":\"aws.appconfig\"}")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message1, message2).build());

        SourceChangeEvent event = mock(SourceChangeEvent.class);
        when(eventParser.parse(any())).thenReturn(event);
        when(sourceRegistry.getValues()).thenReturn(Collections.emptyList());

        // when
        listener.pollAndRefresh();

        // then
        verify(eventParser, times(2)).parse(any());
        verify(sqsClient, times(1)).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotCallRefreshWhenNoSourceMatchesEvent() throws Exception {
        // given
        Message message = Message.builder()
                .messageId("msg-1")
                .receiptHandle("handle-1")
                .body("{\"source\":\"aws.unknown\"}")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        SourceChangeEvent event = mock(SourceChangeEvent.class);
        when(eventParser.parse(message.body())).thenReturn(event);

        Source<Configuration<?>> source = mock(Source.class);
        when(source.onChanged(event)).thenReturn(false);
        when(sourceRegistry.getValues()).thenReturn(List.of(source));

        // when
        listener.pollAndRefresh();

        // then
        verify(source, never()).refresh();
    }
}

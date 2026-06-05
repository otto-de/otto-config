package de.otto.config.core.aws;

import de.otto.config.core.Context;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceChangeEvent;
import de.otto.config.core.source.SourceChangeEventListener;
import de.otto.config.core.aws.event.ChangeEventParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AwsChangeEventListener implements SourceChangeEventListener {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final Context context;
    private final ChangeEventParser eventParser;

    @Override
    public void pollAndRefresh() {
        List<Message> messages = receiveMessages();
        if (messages.isEmpty()) {
            return;
        }

        log.debug("Received {} message(s) from queue {}", messages.size(), queueUrl);

        List<DeleteMessageBatchRequestEntry> toDelete = new ArrayList<>(messages.size());

        for (Message message : messages) {
            try {
                SourceChangeEvent event = eventParser.parse(message.body());
                dispatchEvent(event);
            } catch (Exception e) {
                log.error("Failed to process SQS message {} — discarding to avoid poison-pill loop",
                        message.messageId(), e);
            }
            // Always schedule for deletion: unrecognised or failed messages should not
            // re-drive the queue. Genuine retries are handled by the safety-net scheduler.
            toDelete.add(DeleteMessageBatchRequestEntry.builder()
                    .id(message.messageId())
                    .receiptHandle(message.receiptHandle())
                    .build());
        }

        deleteMessages(toDelete);
    }

    private void dispatchEvent(SourceChangeEvent event) {
        boolean anyMatch = false;
        for (Source<?> source : this.context.getSourceRegistry().getValues()) {
            if (source.onChanged(event)) {
                source.refresh();
                anyMatch = true;
            }
        }
        if (!anyMatch) {
            log.debug("No registered source matched event source='{}' detail-type='{}'",
                    event.source(), event.detailType());
        }
    }

    private List<Message> receiveMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .waitTimeSeconds(20)
                .maxNumberOfMessages(10)
                .build();
        return sqsClient.receiveMessage(request).messages();
    }

    private void deleteMessages(List<DeleteMessageBatchRequestEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build());
    }
}

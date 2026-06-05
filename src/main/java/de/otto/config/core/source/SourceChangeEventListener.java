package de.otto.config.core.source;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.Context;
import de.otto.config.core.aws.AwsChangeEventListener;
import de.otto.config.core.aws.event.ChangeEventParser;
import software.amazon.awssdk.services.sqs.SqsClient;

@FunctionalInterface
public interface SourceChangeEventListener {
    
    public void pollAndRefresh();

    public static List<SourceChangeEventListener> from(Context context) {
        boolean changeNotificationsEnabled = context.getConfiguration().getValueAsBoolean("otto.config.aws.change.notifications.enabled", false);
        String queueUrl = context.getConfiguration().getValue("otto.config.aws.change.notifications.queue.url", "");
        if (changeNotificationsEnabled && !queueUrl.isBlank()) {
            SqsClient sqsClient = SqsClient.create();
            ChangeEventParser parser = new ChangeEventParser(new ObjectMapper());
            return List.of(new AwsChangeEventListener(sqsClient, queueUrl, context, parser));
        }
        return List.of();
    }
}

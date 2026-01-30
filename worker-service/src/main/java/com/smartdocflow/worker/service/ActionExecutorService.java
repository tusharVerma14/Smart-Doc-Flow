package com.smartdocflow.worker.service;

import com.smartdocflow.worker.dto.WorkflowRuleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionExecutorService {

    private final JavaMailSender mailSender;
    private final WebClient.Builder webClientBuilder;

    /**
     * Executes a single action based on its type - NOW USES POJO
     */
    public void executeAction(WorkflowRuleDTO.DecisionAction.ActionItem action,
                              Map<String, Object> context) {
        String actionType = action.getActionType();
        Boolean async = action.getAsync() != null ? action.getAsync() : false;
        Integer retryCount = action.getRetryCount() != null ? action.getRetryCount() : 0;

        @SuppressWarnings("unchecked")
        Map<String, Object> actionConfig = (Map<String, Object>) action.getActionConfig();

        log.info("Executing action: {} (async: {})", actionType, async);

        if (Boolean.TRUE.equals(async)) {
            executeActionAsync(actionType, actionConfig, context, retryCount);
        } else {
            executeActionSync(actionType, actionConfig, context, retryCount);
        }
    }

    /**
     * Executes action synchronously with retry logic
     */
    private void executeActionSync(String actionType,
                                   Map<String, Object> actionConfig,
                                   Map<String, Object> context,
                                   int retryCount) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts <= retryCount) {
            try {
                executeActionByType(actionType, actionConfig, context);
                log.info("Action {} executed successfully", actionType);
                return;
            } catch (Exception e) {
                lastException = e;
                attempts++;
                if (attempts <= retryCount) {
                    log.warn("Action {} failed (attempt {}/{}), retrying...",
                            actionType, attempts, retryCount + 1);
                    try {
                        Thread.sleep(1000L * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Action {} failed after {} attempts", actionType, attempts, lastException);
    }

    /**
     * Executes action asynchronously
     */
    @Async
    public CompletableFuture<Void> executeActionAsync(String actionType,
                                                      Map<String, Object> actionConfig,
                                                      Map<String, Object> context,
                                                      int retryCount) {
        return CompletableFuture.runAsync(() ->
                executeActionSync(actionType, actionConfig, context, retryCount));
    }

    /**
     * Routes to appropriate action handler based on type
     */
    private void executeActionByType(String actionType,
                                     Map<String, Object> actionConfig,
                                     Map<String, Object> context) {
        switch (actionType.toUpperCase()) {
            case "EMAIL":
                sendEmail(actionConfig, context);
                break;

            case "SMS":
                sendSms(actionConfig, context);
                break;

            case "WEBHOOK":
                callWebhook(actionConfig, context);
                break;

            case "DATABASE_UPDATE":
                updateDatabase(actionConfig, context);
                break;

            case "QUEUE_MESSAGE":
                sendQueueMessage(actionConfig, context);
                break;

            case "SLACK_NOTIFICATION":
                sendSlackNotification(actionConfig, context);
                break;

            case "TEAMS_NOTIFICATION":
                sendTeamsNotification(actionConfig, context);
                break;

            case "CREATE_TASK":
                createTask(actionConfig, context);
                break;

            case "UPDATE_STATUS":
                updateApplicationStatus(actionConfig, context);
                break;

            default:
                log.warn("Unknown action type: {}", actionType);
        }
    }

    /**
     * Sends email notification
     */
    private void sendEmail(Map<String, Object> config, Map<String, Object> context) {
        try {
            String to = interpolate((String) config.get("to"), context);
            String subject = interpolate((String) config.get("subject"), context);
            String body = interpolate((String) config.get("body"), context);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            if (config.containsKey("cc")) {
                message.setCc(interpolate((String) config.get("cc"), context));
            }

            mailSender.send(message);
            log.info("Email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send email", e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    /**
     * Sends SMS notification
     */
    private void sendSms(Map<String, Object> config, Map<String, Object> context) {
        try {
            String phoneNumber = interpolate((String) config.get("phoneNumber"), context);
            String message = interpolate((String) config.get("message"), context);

            // Implement SMS sending logic here
            // Example: twilioService.sendSms(phoneNumber, message);

            log.info("SMS sent to: {}", phoneNumber);

        } catch (Exception e) {
            log.error("Failed to send SMS", e);
            throw new RuntimeException("SMS sending failed", e);
        }
    }

    /**
     * Calls external webhook
     */
    private void callWebhook(Map<String, Object> config, Map<String, Object> context) {
        try {
            String url = (String) config.get("url");
            String method = (String) config.getOrDefault("method", "POST");

            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) config.get("headers");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) config.get("body");

            // Interpolate body values
            Map<String, Object> interpolatedBody = interpolateMap(body, context);

            WebClient.RequestBodySpec request = (WebClient.RequestBodySpec) webClientBuilder.build()
                    .method(org.springframework.http.HttpMethod.valueOf(method.toUpperCase()))
                    .uri(url)
                    .bodyValue(interpolatedBody);

            // Add headers if present
            if (headers != null) {
                headers.forEach((key, value) ->
                        request.header(key, interpolate(value.toString(), context)));
            }

            String response = request.retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Webhook called successfully: {} - Response: {}", url, response);

        } catch (Exception e) {
            log.error("Failed to call webhook", e);
            throw new RuntimeException("Webhook call failed", e);
        }
    }

    /**
     * Updates database record
     */
    private void updateDatabase(Map<String, Object> config, Map<String, Object> context) {
        try {
            String table = (String) config.get("table");
            String operation = (String) config.getOrDefault("operation", "UPDATE");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) config.get("data");

            // Interpolate data values
            Map<String, Object> interpolatedData = interpolateMap(data, context);

            // Implement database update logic
            log.info("Database {} operation on table: {} with data: {}",
                    operation, table, interpolatedData);

        } catch (Exception e) {
            log.error("Failed to update database", e);
            throw new RuntimeException("Database update failed", e);
        }
    }

    /**
     * Sends message to queue
     */
    private void sendQueueMessage(Map<String, Object> config, Map<String, Object> context) {
        try {
            String queueName = (String) config.get("queueName");

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) config.get("message");

            Map<String, Object> interpolatedMessage = interpolateMap(message, context);

            // Implement queue message sending logic
            // Example: rabbitTemplate.convertAndSend(queueName, interpolatedMessage);

            log.info("Message sent to queue: {} with payload: {}",
                    queueName, interpolatedMessage);

        } catch (Exception e) {
            log.error("Failed to send queue message", e);
            throw new RuntimeException("Queue message sending failed", e);
        }
    }

    /**
     * Sends Slack notification
     */
    private void sendSlackNotification(Map<String, Object> config, Map<String, Object> context) {
        try {
            String webhookUrl = (String) config.get("webhookUrl");
            String channel = (String) config.get("channel");
            String message = interpolate((String) config.get("message"), context);

            Map<String, Object> slackPayload = Map.of(
                    "channel", channel,
                    "text", message
            );

            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(slackPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Slack notification sent to channel: {}", channel);

        } catch (Exception e) {
            log.error("Failed to send Slack notification", e);
            throw new RuntimeException("Slack notification failed", e);
        }
    }

    /**
     * Sends Microsoft Teams notification
     */
    private void sendTeamsNotification(Map<String, Object> config, Map<String, Object> context) {
        try {
            String webhookUrl = (String) config.get("webhookUrl");
            String message = interpolate((String) config.get("message"), context);

            Map<String, Object> teamsPayload = Map.of(
                    "text", message
            );

            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(teamsPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Teams notification sent");

        } catch (Exception e) {
            log.error("Failed to send Teams notification", e);
            throw new RuntimeException("Teams notification failed", e);
        }
    }

    /**
     * Creates a task in task management system
     */
    private void createTask(Map<String, Object> config, Map<String, Object> context) {
        try {
            String assignee = interpolate((String) config.get("assignee"), context);
            String title = interpolate((String) config.get("title"), context);
            String description = interpolate((String) config.get("description"), context);
            String priority = (String) config.getOrDefault("priority", "MEDIUM");

            // Implement task creation logic
            log.info("Task created: {} assigned to {} with priority {}",
                    title, assignee, priority);

        } catch (Exception e) {
            log.error("Failed to create task", e);
            throw new RuntimeException("Task creation failed", e);
        }
    }

    /**
     * Updates application status
     */
    private void updateApplicationStatus(Map<String, Object> config, Map<String, Object> context) {
        try {
            String applicationId = interpolate((String) config.get("applicationId"), context);
            String newStatus = interpolate((String) config.get("status"), context);

            // Implement status update logic
            log.info("Application {} status updated to {}", applicationId, newStatus);

        } catch (Exception e) {
            log.error("Failed to update application status", e);
            throw new RuntimeException("Status update failed", e);
        }
    }

    /**
     * Interpolates template string with context values
     * Example: "Hello ${customerName}" with context {customerName: "John"} -> "Hello John"
     * IMPROVED: Uses regex for better performance and reliability
     */
    private String interpolate(String template, Map<String, Object> context) {
        if (template == null) return null;
        if (context == null) return template;

        String result = template;

        // Use regex to find all ${...} patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(0); // Full ${key}
            String key = matcher.group(1); // Just the key

            Object value = context.get(key);
            String replacement = value != null ? value.toString() : "";

            result = result.replace(placeholder, replacement);
        }

        return result;
    }

    /**
     * Interpolates all string values in a map
     */
    private Map<String, Object> interpolateMap(Map<String, Object> map,
                                               Map<String, Object> context) {
        if (map == null) return null;

        Map<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                result.put(entry.getKey(), interpolate((String) value, context));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                result.put(entry.getKey(), interpolateMap(nestedMap, context));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
}
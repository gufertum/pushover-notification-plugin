package org.rundeck.plugins.notification;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;
import net.pushover.client.MessagePriority.*;
import net.pushover.client.*;
import java.util.*;

/**
 * Pushover Notification Plugin
 */
@Plugin(service = "Notification", name = "Pushover")
@PluginDescription(title = "Pushover", description = "Send notification messages to Pushover")
public class PushoverNotification implements NotificationPlugin {

	@PluginProperty(name = "appApiToken", description = "Application API Token", scope = PropertyScope.Project)
	private String appApiToken;

	@PluginProperty(name = "userIdToken", description = "User ID Token", scope = PropertyScope.Project)
	private String userIdToken;

	/**
	 * Post a notification for the given trigger, dataset, and configuration
	 *
	 * @param trigger event type causing notification
	 * @param executionData execution data
	 * @param config notification configuration
	 * @return
	 */
	@Override
	public boolean postNotification(String trigger, Map executionData, Map config) {
		if (isBlank(appApiToken) || isBlank(userIdToken)) {
			throw new IllegalStateException("appApiToken and userIdToken must be set");
		}

		//https://docs.rundeck.com/docs/developer/notification-plugin.html#execution-data

        String jobName = ((Map)executionData.get("job")).get("name").toString();
		//String jobGroup = ((Map)executionData.get("job")).get("group").toString();
		//String jobProject = ((Map)executionData.get("job")).get("project").toString();

		String url = executionData.get("href").toString(); //URL to the execution output view

		PushoverClient client = new PushoverRestClient();

		//set prio to high only if the job really failed
		MessagePriority prio = MessagePriority.NORMAL;
		if(trigger != null && "failure".equalsIgnoreCase(trigger.toLowerCase())) {
			prio = MessagePriority.HIGH;
		}

        try {
            client.pushMessage(PushoverMessage.builderWithApiToken(appApiToken)
                    .setUserId(userIdToken)
					.setTitle(getNotificationTitle(trigger, jobName))
                    .setMessage(getNotificationMessage(trigger, jobName, executionData))
					.setUrl(url)
					.setPriority(prio)
                    .build());
        } catch (PushoverException e) {
                    e.printStackTrace();
                    return false;
        }

        //System.err.printf("Trigger was: %s \n", trigger);
        //System.err.printf("Execution data was: %s \n", executionData);
        //System.err.printf("Config was: %s\n", config);
        //System.err.printf("Jobdata was: %s\n", jobString);
        //System.err.printf("Project was: %s\n", projectString);

		return true;
	}

	private boolean isBlank(String string) {
		return null == string || "".equals(string);
	}


	private String getNotificationTitle(String trigger, String job) {
		String notificationMessage = null;
		switch (trigger) {
			case "start": // the Job started
				notificationMessage = "Job '" + job + "' has started.";
				break;
			case "success": // the Job completed without error
				notificationMessage = "Job '" + job  + "' has finished successfully!";
				break;
			case "failure": //  the Job failed or was aborted
				notificationMessage = "Job '" + job + "' has failed!";
				break;
			case "retryablefailure": //  the Job failed but will be retried
				notificationMessage = "Job '" + job + "' has failed, but will retry.";
				break;
			case "onavgduration": // The Execution exceed the average duration of the Job
				notificationMessage = "Job '" + job + "' exeeded avg duration";
				break;
			default: // undefined or new trigger method
				notificationMessage = "Job '" + job + "' triggered by: " + trigger;
				break;
		}
		return notificationMessage;
	}

	private String getNotificationMessage(String trigger, String job, Map executionData) {

		//String project = executionData.get("project").toString();
		String id = executionData.get("id").toString(); //execution id
		String status = executionData.get("status").toString(); // ‘running’,‘failed’,‘aborted’,‘succeeded’

		StringBuffer b = new StringBuffer();
		b.append("Execution #" + id + " >> " + status).append("\n");

		// TODO build all necessary info frmo the executionData (like in the email)

		return b.toString();
	}

}

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
					.setTitle(getNotificationTitle(trigger, executionData))
                    .setMessage(getNotificationMessage(trigger, executionData))
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


	private String getNotificationTitle(String trigger, Map executionData) {

		String job = ((Map)executionData.get("job")).get("name").toString();

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

	private String getNotificationMessage(String trigger, Map executionData) {
		Object job = executionData.get("job");

		Map jobdata = (Map) job;

		Date date = (trigger.equals("running") ? (Date)executionData.get("dateStarted") : (Date)executionData.get("dateEnded"));

		StringBuilder sb = new StringBuilder();

		String status = (String)executionData.get("status");

		sb.append("Job [").append(trigger.toUpperCase()).append("]");
		sb.append(" #"+executionData.get("id")).append(" ").append(status).append("\n");

		sb.append("   started by ").append(executionData.get("user")).append("\n");

		if ("aborted".equalsIgnoreCase("status") && executionData.get("abortedby") != null) {
			sb.append("    aborted by ").append(executionData.get("abortedby")).append("\n");
		}
		if (date != null) {
			sb.append("    at ").append(date).append("\n");
		}

		//display description if set
		String jobdesc = (String)jobdata.get("description");
		if (!isBlank(jobdesc)) {
			sb.append("Description: ").append(jobdesc).append("\n");
		}

		//build job breadcrumb
		String project = (String)jobdata.get("project");
		String jobgroup = (String)jobdata.get("group");
		String jobname = (String)jobdata.get("name");

		sb.append("Breadcrumb: ").append(project);
		if (!isBlank(jobgroup)) {
			sb.append(" > ").append(jobgroup);
		}
		if (!isBlank(jobname)) {
			sb.append(" > ").append(jobname);
		}
		sb.append("\n");

		Map context = (Map) executionData.get("context");

		Map options = (Map) context.get("option");
		Map secureOption = (Map) context.get("secureOption");
		if (null != options && options.size() > 0) {
			sb.append("User Options\n");
			for (Object o : options.entrySet()) {
				Map.Entry entry = (Map.Entry) o;
				if (secureOption == null || !secureOption.containsKey(entry.getKey())) {
					sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
				}
			}
		}
		Map nodestatus = (Map)executionData.get("nodestatus");
		if (nodestatus != null) {
			sb.append("\n");
			sb.append("Nodes status [ ");
			sb.append("failed=").append(nodestatus.get("failed"));
			sb.append(" succeeded=").append(nodestatus.get("succeeded"));
			sb.append(" total=").append(nodestatus.get("total")).append(" ]\n");
		}

		String nodesFailed = (String)executionData.get("failedNodeListString");
		if (nodesFailed != null) {
			sb.append("Nodes failed: ").append(nodesFailed).append("\n");
		}

		String nodesSucceeded = (String)executionData.get("succeededNodeListString");
		if (nodesSucceeded != null) {
			sb.append("Nodes succeeded: ").append(nodesSucceeded).append("\n");
		}

		return sb.toString();
	}
}

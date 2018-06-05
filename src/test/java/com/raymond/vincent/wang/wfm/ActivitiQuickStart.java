package com.raymond.vincent.wang.wfm;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormData;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.activiti.engine.impl.form.DateFormType;
import org.activiti.engine.impl.form.LongFormType;
import org.activiti.engine.impl.form.StringFormType;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivitiQuickStart {

	private static final Logger log = Logger.getLogger(ActivitiQuickStart.class);

	private static final String JDBC_URL = "jdbc:h2:mem:activiti;DB_CLOSE_DELAY=1000";
	private static final String JDBC_DRIVER = "org.h2.Driver";
	private static final String JDBC_USERNAME = "sa";
	private static final String JDBC_PASSWORD = "";

	private static final int JDBC_MAX_ACTIVE_CONNECTIONS = 10;
	private static final int JDBC_MAX_IDLE_CONNECTIONS = 4;
	private static final int JDBC_MAX_CHECKOUT_TIME = 20 * 1000;
	private static final int JDBC_MAX_WAIT_TIME = 20 * 1000;

	private static ProcessEngine processEngine;

	@BeforeClass
	public static void startUp() {

		ProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration().setJdbcUrl(JDBC_URL)
				.setJdbcDriver(JDBC_DRIVER).setJdbcUsername(JDBC_USERNAME).setJdbcPassword(JDBC_PASSWORD)
				.setJdbcMaxActiveConnections(JDBC_MAX_ACTIVE_CONNECTIONS)
				.setJdbcMaxIdleConnections(JDBC_MAX_IDLE_CONNECTIONS).setJdbcMaxCheckoutTime(JDBC_MAX_CHECKOUT_TIME)
				.setJdbcMaxWaitTime(JDBC_MAX_WAIT_TIME)
				.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP);

		processEngine = cfg.buildProcessEngine();
	}

	@Test
	public void quickStart() throws ParseException {

		String processEngineName = processEngine.getName();
		String ProcessEngineVersion = ProcessEngine.VERSION;

		log.info("ProcessEngine [" + processEngineName + "] Version: [" + ProcessEngineVersion + "]");

		RepositoryService repositoryService = processEngine.getRepositoryService();
		Deployment deployment = repositoryService.createDeployment().addClasspathResource("onboarding.bpmn20.xml")
				.deploy();
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
				.deploymentId(deployment.getId()).singleResult();

		log.info("Found process definition [" + processDefinition.getName() + "] with id [" + processDefinition.getId()
				+ "]");

		RuntimeService runtimeService = processEngine.getRuntimeService();
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("onboarding");
		log.info("Onboarding process started with process instance id [" + processInstance.getProcessInstanceId()
				+ "] key [" + processInstance.getProcessDefinitionKey() + "]");

		TaskService taskService = processEngine.getTaskService();
		FormService formService = processEngine.getFormService();
		HistoryService historyService = processEngine.getHistoryService();

		Scanner scanner = new Scanner(System.in);

		while (processInstance != null && !processInstance.isEnded()) {

			List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();

			log.info("Active outstanding tasks: [" + tasks.size() + "]");

			for (int i = 0; i < tasks.size(); i++) {

				Task task = tasks.get(i);

				log.info("Processing Task [" + task.getName() + "]");

				Map<String, Object> variables = new HashMap<String, Object>();

				FormData formData = formService.getTaskFormData(task.getId());

				for (FormProperty formProperty : formData.getFormProperties()) {
					if (StringFormType.class.isInstance(formProperty.getType())) {
						log.info(formProperty.getName() + "?");
						String value = scanner.nextLine();
						variables.put(formProperty.getId(), value);
					} else if (LongFormType.class.isInstance(formProperty.getType())) {
						log.info(formProperty.getName() + "? (Must be a whole number)");
						Long value = Long.valueOf(scanner.nextLine());
						variables.put(formProperty.getId(), value);
					} else if (DateFormType.class.isInstance(formProperty.getType())) {
						log.info(formProperty.getName() + "? (Must be a date m/d/yy)");
						DateFormat dateFormat = new SimpleDateFormat("m/d/yy");
						Date value = dateFormat.parse(scanner.nextLine());
						variables.put(formProperty.getId(), value);
					} else {
						log.info("<form type not supported>");
					}
				}

				taskService.complete(task.getId(), variables);

				HistoricActivityInstance endActivity = null;

				List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
						.processInstanceId(processInstance.getId()).finished().orderByHistoricActivityInstanceEndTime()
						.asc().list();

				for (HistoricActivityInstance activity : activities) {
					if ("startEvent".equals(activity.getActivityType())) {
						log.info("BEGIN " + processDefinition.getName() + " ["
								+ processInstance.getProcessDefinitionKey() + "] " + activity.getStartTime());
					}
					if ("endEvent".equals(activity.getActivityType())) {
						// Handle edge case where end step happens so fast that the end step
						// and previous step(s) are sorted the same. So, cache the end step
						// and display it last to represent the logical sequence.
						endActivity = activity;
					} else {
						log.info("-- " + activity.getActivityName() + " [" + activity.getActivityId() + "] "
								+ activity.getDurationInMillis() + " ms");
					}
				}

				if (endActivity != null) {
					log.info("-- " + endActivity.getActivityName() + " [" + endActivity.getActivityId() + "] "
							+ endActivity.getDurationInMillis() + " ms");
					log.info("COMPLETE " + processDefinition.getName() + " ["
							+ processInstance.getProcessDefinitionKey() + "] " + endActivity.getEndTime());
				}

			}

			// Re-query the process instance, making sure the latest state is available
			processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId())
					.singleResult();
		}

		scanner.close();

	}

	@AfterClass
	public static void shutDown() {
		processEngine.close();
	}

}

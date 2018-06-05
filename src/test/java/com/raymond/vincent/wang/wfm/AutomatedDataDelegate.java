package com.raymond.vincent.wang.wfm;

import java.util.Date;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.apache.log4j.Logger;

public class AutomatedDataDelegate implements JavaDelegate {

	private static final Logger log = Logger.getLogger(AutomatedDataDelegate.class);

	public void execute(DelegateExecution execution) {
		Date now = new Date();
		execution.setVariable("autoWelcomeTime", now);
		log.info("Faux call to backend for [" + execution.getVariable("fullName") + "]");
	}

}

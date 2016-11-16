package com.verint.bassworkflow;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.cmmn.Cmmn;
import org.camunda.bpm.model.cmmn.CmmnModelInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.InputStream;

@SpringBootApplication
@EnableTransactionManagement
public class Application {
    static ProcessEngine processEngine;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);

        processEngine = createProcessEngine();

        // deploy the necessary definitions
        deployCaseDefinition();
        deployNoOpProcess();

        CaseInstance caseInstance = createCaseInstance();

        // set the "shouldPublishImmediately" variable
        setVariable(caseInstance);

        // Start in the new stage, enable the Publish task
        enablePublishTask(caseInstance);
        completePublishTask();
        completeNewStage();
    }

    private static void setVariable(CaseInstance caseInstance) {
        processEngine.getCaseService().setVariable(caseInstance.getId(), "shouldPublishImmediately", true);
    }

    private static CaseInstance createCaseInstance() {
        return processEngine.getCaseService().createCaseInstanceByKey("ContentAuthoring");
    }

    private static void completeNewStage() {
        CaseExecution newStageExecution
                = processEngine.getCaseService().createCaseExecutionQuery().activityId("PlanItem_NewStage").singleResult();
        processEngine.getCaseService().completeCaseExecution(newStageExecution.getId());
    }

    private static void completePublishTask() {
        Task publishTask = processEngine.getTaskService().createTaskQuery().taskDefinitionKey("PlanItem_PublishNew").singleResult();
        processEngine.getTaskService().complete(publishTask.getId());
    }

    private static CaseExecution enablePublishTask(CaseInstance caseInstance) {
        CaseExecution publishExecution = processEngine.getCaseService()
                .createCaseExecutionQuery()
                .caseInstanceId(caseInstance.getId())
                .activityId("PlanItem_PublishNew").singleResult();

        processEngine.getCaseService().manuallyStartCaseExecution(publishExecution.getId());
        return publishExecution;
    }

    private static void deployCaseDefinition() {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        String filename = "ContentAuthoring.cmmn11.xml";
        InputStream resourceAsStream = classLoader.getResourceAsStream(filename);
        CmmnModelInstance cmmnModelInstance = Cmmn.readModelFromStream(resourceAsStream);

        processEngine.getRepositoryService()
                .createDeployment()
                .name("content-authoring")
                .addString("content-authoring.cmmn11.xml", Cmmn.convertToString(cmmnModelInstance))
                .deploy();
    }

    private static void deployNoOpProcess() {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream resourceStream = classLoader.getResourceAsStream("NoOp.bpmn");

        BpmnModelInstance bpmn = Bpmn.readModelFromStream(resourceStream);
        processEngine.getRepositoryService()
                .createDeployment().name("No Op")
                .addModelInstance("NoOp.bpmn", bpmn)
                .deploy();
    }

    private static ProcessEngine createProcessEngine() {
        ProcessEngineConfiguration config =
                ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();

        return config
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP)
                .setJdbcUrl("jdbc:h2:mem:my-own-db;DB_CLOSE_DELAY=1000")
                .buildProcessEngine();
    }
}

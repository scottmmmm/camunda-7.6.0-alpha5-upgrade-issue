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
import java.util.List;

@SpringBootApplication
@EnableTransactionManagement
public class Application {
    static ProcessEngine processEngine;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);

        CaseInstance caseInstance = setUp();
        if (args.length != 0 && args[0].equals("sendDirectlyForManagerReview")) {
            moveStraightToManagerReview(caseInstance);
        } else {
            moveToManagerReviewAfterSendingForRework(caseInstance);
        }
    }

    private static CaseInstance setUp() {
        processEngine = createProcessEngine();

        // deploy the necessary definitions
        deployCaseDefinition();

        return createCaseInstance();
    }

    private static void moveToManagerReviewAfterSendingForRework(CaseInstance caseInstance) {
        // Start in the new stage, enable the Publish task
        enableTask(caseInstance, "PlanItem_SubmitNew");
        completeTask("PlanItem_SubmitNew");
        completeStage("PlanItem_NewStage");

        // The case will have moved to the "Content Review" stage.
        // From here we will reject and move to rework
        enableTask(caseInstance, "PlanItem_RejectReview");
        completeTask("PlanItem_RejectReview");
        completeTask("PlanItem_Review");

        // The case will now be in the "Rework" stage. We will submit for review again
        enableTask(caseInstance, "PlanItem_SubmitRework");
        completeTask("PlanItem_SubmitRework");
        completeTask("PlanItem_Rework");

        // The case will now be back in the "Content Review" stage. This time we will Approve
        enableTask(caseInstance, "PlanItem_ApproveReview");
        completeTask("PlanItem_ApproveReview");
        completeTask("PlanItem_Review");

        // An instance of the the "Manager Review" stage should become active. There should be one active Task (Review) and two enabled ones (Approve/Reject)
        List<Task> active = processEngine.getTaskService().createTaskQuery().caseInstanceId(caseInstance.getId()).list();
        System.out.println("There are " + active.size() + " active Tasks");
        active.stream()
                .forEach(task -> System.out.println("Name = " + task.getTaskDefinitionKey()));
    }

    private static void moveStraightToManagerReview(CaseInstance caseInstance) {
        // Start in the new stage, enable the Publish task
        enableTask(caseInstance, "PlanItem_SubmitNew");
        completeTask("PlanItem_SubmitNew");
        completeStage("PlanItem_NewStage");

        // The case will have moved to the "Content Review" stage.
        // From here we will approve and move straight to "Manager Review"
        enableTask(caseInstance, "PlanItem_ApproveReview");
        completeTask("PlanItem_ApproveReview");
        completeTask("PlanItem_Review");

        // An instance of the the "Manager Review" stage should become active. There should be one active Task (Review) and two enabled ones (Approve/Reject)
        List<Task> active = processEngine.getTaskService().createTaskQuery().caseInstanceId(caseInstance.getId()).list();
        System.out.println("There are " + active.size() + " active Tasks");
        active.stream()
                .forEach(task -> System.out.println("Name = " + task.getTaskDefinitionKey()));
    }

    private static void setVariable(CaseInstance caseInstance) {
        processEngine.getCaseService().setVariable(caseInstance.getId(), "shouldPublishImmediately", true);
    }

    private static CaseInstance createCaseInstance() {
        return processEngine.getCaseService().createCaseInstanceByKey("ContentAuthoring");
    }

    private static void completeStage(String stageActivityId) {
        CaseExecution newStageExecution
                = processEngine.getCaseService().createCaseExecutionQuery().activityId(stageActivityId).singleResult();
        processEngine.getCaseService().completeCaseExecution(newStageExecution.getId());
    }

    private static void completeTask(String taskDefinitionKey) {
        Task task = processEngine.getTaskService().createTaskQuery().taskDefinitionKey(taskDefinitionKey).singleResult();
        processEngine.getTaskService().complete(task.getId());
    }

    private static CaseExecution enableTask(CaseInstance caseInstance, String taskActivityId) {
        CaseExecution publishExecution = processEngine.getCaseService()
                .createCaseExecutionQuery()
                .caseInstanceId(caseInstance.getId())
                .activityId(taskActivityId).singleResult();

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

package com.bonitasoft.presales.procurement;

import static org.awaitility.Awaitility.await;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

import org.assertj.core.api.Assertions;
import static org.assertj.core.groups.Tuple.tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonitasoft.test.toolkit.BonitaTestToolkit;
import com.bonitasoft.test.toolkit.contract.ContractBuilder;
import com.bonitasoft.test.toolkit.junit.extension.BonitaTests;
import com.bonitasoft.test.toolkit.model.QueryParameter;
import com.bonitasoft.test.toolkit.model.UserTask;

import static com.bonitasoft.test.toolkit.predicate.ProcessInstancePredicates.processInstanceCompleted;
import static com.bonitasoft.test.toolkit.predicate.ProcessInstancePredicates.containsPendingUserTasks;

@BonitaTests
class ProcurementRequestIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcurementRequestIT.class);

    @BeforeEach
    void cleanup(BonitaTestToolkit toolkit) {
        toolkit.deleteBDMContent();
        toolkit.deleteProcessInstances();
    }

    @Test
    void testSupplierSelection(BonitaTestToolkit toolkit) {
        initializeSampleData(toolkit);

        var procurementRequestProcess = toolkit.getProcessDefinition("Procurement request");

        var helenKelly = toolkit.getUser("helen.kelly");
        var instance = procurementRequestProcess.startProcessFor(helenKelly, ContractBuilder.newContract()
                .textInput("summary", "Some request summary")
                .textInput("description", "Some request description")
                .multipleTextInput("supplierIds",
                        List.of(findSupplierId(toolkit, "Acme Inc."), findSupplierId(toolkit, "Donut Co.")))
                .build());

        await("Complete Quotation tasks are pending.").until(instance, containsPendingUserTasks("Complete quotation"));

        var quotations = instance.getMultipleBusinessData("quotations", Quotation.class);
        assertEquals(2, quotations.size(), "Invalid number of quotations");

        List<UserTask> tasks = instance.searchPendingUserTasks("Complete quotation");
        assertEquals(2, tasks.size(), "Invalid number of complete quotations tasks");
        for (var task : tasks) {
            var quotation = task.getIteratorBusinessVariable("quotation", Quotation.class);
            assertEquals("Pending", quotation.getStatus(), "Invalid quotation status");
            assertEquals("Helen Kelly", quotation.getRequest().getCreatedBy(), "Invalid created by");
            // Other assertions can be added...
        }

        var giovannaAlmeida = toolkit.getUser("giovanna.almeida");
        var completeAcmeQuotationTask = getCompleteQuotationTaskBySupplier(toolkit, "Acme Inc.", tasks);
        var completeDonutQuotationTask = getCompleteQuotationTaskBySupplier(toolkit, "Donut Co.", tasks);

        Assertions.assertThat(completeAcmeQuotationTask.getCandidates()).contains(giovannaAlmeida);
        completeAcmeQuotationTask.execute(giovannaAlmeida, ContractBuilder.newContract()
                .booleanInput("hasSupplierAccepted", true)
                .decimalInput("price", 500d)
                .textInput("comments", "Best price available")
                .build());

        var patrickGardenier = toolkit.getUser("patrick.gardenier");
        Assertions.assertThat(completeDonutQuotationTask.getCandidates()).contains(patrickGardenier);
        completeDonutQuotationTask.execute(patrickGardenier, ContractBuilder.newContract()
                .booleanInput("hasSupplierAccepted", true)
                .decimalInput("price", 250d)
                .textInput("comments", "Excellent price available")
                .build());

        await("Review task available").until(instance, containsPendingUserTasks("Review quotations & select supplier"));

        quotations = instance.getMultipleBusinessData("quotations", Quotation.class);
        Assertions.assertThat(quotations).allSatisfy(q -> Assertions.assertThat(q.getStatus()).isEqualTo("Completed"))
                .as("All quotations has been completed");
        Assertions.assertThat(quotations).allSatisfy(q -> Assertions.assertThat(q.getHasSupplierAccepted()).isTrue())
                .as("All quotations has been accepted");

        var request = instance.getBusinessData("request", Request.class);
        assertEquals("Pending for review", request.getStatus(), "Request status is pending for review");

        instance.getFirstPendingUserTask("Review quotations & select supplier").execute(helenKelly, ContractBuilder
                .newContract().textInput("selectedSupplierId", findSupplierId(toolkit, "Acme Inc.")).build());

        await("Process is completed").until(instance, processInstanceCompleted());
        request = instance.getBusinessData("request", Request.class);
        assertEquals("Completed", request.getStatus(), "Request status is completed");
        assertEquals(findSupplier(toolkit, "Acme Inc.").getPersistenceId(),
                request.getSelectedSupplier().getPersistenceId(), "Acme Inc. is selected");
    }

     @Test
    void testNoSupplierSelected(BonitaTestToolkit toolkit) {
        initializeSampleData(toolkit);

        var procurementRequestProcess = toolkit.getProcessDefinition("Procurement request");

        var helenKelly = toolkit.getUser("helen.kelly");
        var instance = procurementRequestProcess.startProcessFor(helenKelly, ContractBuilder.newContract()
                .textInput("summary", "Some request summary")
                .textInput("description", "Some request description")
                .multipleTextInput("supplierIds",
                        List.of(findSupplierId(toolkit, "Acme Inc."), findSupplierId(toolkit, "Donut Co.")))
                .build());

        await("Complete Quotation tasks are pending.").until(instance, containsPendingUserTasks("Complete quotation"));

        var quotations = instance.getMultipleBusinessData("quotations", Quotation.class);
        assertEquals(2, quotations.size(), "Invalid number of quotations");

        List<UserTask> tasks = instance.searchPendingUserTasks("Complete quotation");
        assertEquals(2, tasks.size(), "Invalid number of complete quotations tasks");
        for (var task : tasks) {
            var quotation = task.getIteratorBusinessVariable("quotation", Quotation.class);
            assertEquals("Pending", quotation.getStatus(), "Invalid quotation status");
            assertEquals("Helen Kelly", quotation.getRequest().getCreatedBy(), "Invalid created by");
            // Other assertions can be added...
        }

        var giovannaAlmeida = toolkit.getUser("giovanna.almeida");
        var completeAcmeQuotationTask = getCompleteQuotationTaskBySupplier(toolkit, "Acme Inc.", tasks);
        var completeDonutQuotationTask = getCompleteQuotationTaskBySupplier(toolkit, "Donut Co.", tasks);

        Assertions.assertThat(completeAcmeQuotationTask.getCandidates()).contains(giovannaAlmeida);
        completeAcmeQuotationTask.execute(giovannaAlmeida, ContractBuilder.newContract()
                .booleanInput("hasSupplierAccepted", true)
                .decimalInput("price", 500d)
                .textInput("comments", "Best price available")
                .build());

        var patrickGardenier = toolkit.getUser("patrick.gardenier");
        Assertions.assertThat(completeDonutQuotationTask.getCandidates()).contains(patrickGardenier);
        completeDonutQuotationTask.execute(patrickGardenier, ContractBuilder.newContract()
                .booleanInput("hasSupplierAccepted", true)
                .decimalInput("price", 250d)
                .textInput("comments", "Excellent price available")
                .build());

        await("Review task available").until(instance, containsPendingUserTasks("Review quotations & select supplier"));

        quotations = instance.getMultipleBusinessData("quotations", Quotation.class);
        Assertions.assertThat(quotations).allSatisfy(q -> Assertions.assertThat(q.getStatus()).isEqualTo("Completed"))
                .as("All quotations has been completed");
        Assertions.assertThat(quotations).allSatisfy(q -> Assertions.assertThat(q.getHasSupplierAccepted()).isTrue())
                .as("All quotations has been accepted");

        var request = instance.getBusinessData("request", Request.class);
        assertEquals("Pending for review", request.getStatus(), "Request status is pending for review");

        instance.getFirstPendingUserTask("Review quotations & select supplier").execute(helenKelly, ContractBuilder
                .newContract().textInput("selectedSupplierId", "").build());

        await("Process is completed").until(instance, processInstanceCompleted());
        request = instance.getBusinessData("request", Request.class);
        assertEquals("Aborted", request.getStatus(), "Request status is aborted");
    }

    private UserTask getCompleteQuotationTaskBySupplier(BonitaTestToolkit toolkit, String supplierName,
            List<UserTask> tasks) {
        var supplier = findSupplier(toolkit, supplierName);
        return tasks.stream()
                .filter(t -> Objects.equals(
                        t.getIteratorBusinessVariable("quotation", Quotation.class).getSupplier().getPersistenceId(),
                        supplier.getPersistenceId()))
                .findFirst()
                .orElseThrow();
    }

    private Supplier findSupplier(BonitaTestToolkit toolkit, String supplierName) {
        var typedSupplierDAO = toolkit.getBusinessObjectDAO("com.company.model.Supplier", Supplier.class);
        return typedSupplierDAO.querySingle("findByName",
                List.of(QueryParameter.stringParameter("name", supplierName)));
    }

    private String findSupplierId(BonitaTestToolkit toolkit, String supplierName) {
        return String.valueOf(findSupplier(toolkit, supplierName).getPersistenceId());
    }

    private List<Supplier> initializeSampleData(BonitaTestToolkit toolkit) {
        LOGGER.info("Initialize Sample Data");
        var initSampleDataProcess = toolkit.getProcessDefinition("Init sample procurement data");
        var walterBates = toolkit.getUser("walter.bates");
        var initSampleDataInstance = initSampleDataProcess.startProcessFor(walterBates);

        await("Init Sample Data is completed.").until(initSampleDataInstance, processInstanceCompleted());

        // Generic usage of BDM
        var supplierDAO = toolkit.getBusinessObjectDAO("com.company.model.Supplier");
        var result = supplierDAO.find(0, 10);
        assertEquals(3, result.size(), "Invalid number suppliers");

        // Typed usage of BDM
        var typedSupplierDAO = toolkit.getBusinessObjectDAO("com.company.model.Supplier", Supplier.class);
        var typedResult = typedSupplierDAO.find(0, 10);
        Assertions.assertThat(typedResult).extracting("name", "description")
                .containsExactly(tuple("Acme Inc.", "Sample description for Acme Inc."),
                        tuple("Duff Co.", "Sample description for Duff Co."),
                        tuple("Donut Co.", "Sample description for Donut Co."));
        return typedResult;
    }

    interface Supplier {
        long getPersistenceId();

        String getName();

        String getDescription();
    }

    interface Request {
        long getPersistenceId();

        long getCaseId();

        String getSummary();

        String getDescription();

        LocalDate getCreationDate();

        String getCreatedBy();

        String getStatus();

        LocalDate getCompletionDate();

        Supplier getSelectedSupplier();

        String getStorageUrl();
    }

    interface Quotation {
        long getPersistenceId();

        Request getRequest();

        Supplier getSupplier();

        String getStatus();

        boolean getHasSupplierAccepted();

        double getProposedPrice();

        String getComments();
    }

}
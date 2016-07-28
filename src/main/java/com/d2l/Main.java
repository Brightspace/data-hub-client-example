package com.d2l;

import static com.d2lvalence.idkeyauth.AuthenticationSecurityFactory.createSecurityContext;
import static java.io.File.createTempFile;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;
import static javax.json.Json.createReader;
import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Calendar;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;

import com.d2lvalence.idkeyauth.ID2LUserContext;

public class Main {

    @FunctionalInterface
    private static interface ErrorMessageFactory {
        String getErrorMessage(int errorCode);
    }

    private static String[] dates = { "2016-05-29T08:00:23.4560000Z",
            "2016-05-29T09:00:23.4560000Z", "2016-05-30T09:01:00.0000000Z",
            "2016-05-31T09:00:00.0000000Z", "2016-05-31T09:02:45.6780000Z" };

    public static void main(String[] args)
            throws URISyntaxException, IOException, InterruptedException {

        String hostUrl = getProperty("hostUrl");
        String dataSetId = getProperty("dataSetId",
                "c1bf7603-669f-4bef-8cf4-651b914c4678");
        String appId = getProperty("appId");
        String appKey = getProperty("appKey");
        String userId = getProperty("userId");
        String userKey = getProperty("userKey");
        String outputFolder = getProperty("outputFolder");

        assertAllArgumentsSpecified(hostUrl, dataSetId, appId, appKey, userId,
                userKey, outputFolder);

        ID2LUserContext iD2lUserContext = createSecurityContext(appId, appKey,
                hostUrl).createUserContext(userId, userKey);

        assertDataSetIdExists(iD2lUserContext, dataSetId);

        int currentMinute = Calendar.getInstance().get(Calendar.MINUTE);
        String startDate = getStartDate(currentMinute);
        String endDate = getEndDate(currentMinute);
        String exportJobId = submitExportJob(iD2lUserContext, dataSetId,
                startDate, endDate);

        pollForCompletedExportJob(iD2lUserContext, exportJobId);

        downloadCompletedExportJob(iD2lUserContext, exportJobId, outputFolder);
    }

    private static void assertAllArgumentsSpecified(String... input) {
        if (asList(input).stream().anyMatch(s -> isEmpty(s))) {
            die("a required argument is missing");
        }
    }

    private static void die(String errorMessage) {
        System.err.println(format("ERROR: %s!", errorMessage));
        System.err.println();
        printUsage(System.err);
        exit(1);
    }

    private static void printUsage(PrintStream printStream) {
        printStream.println("usage: java -jar <jarfile> ");
        printStream.println("\t-DhostUrl=<hostUrl> -DdataSetId=<reportId>");
        printStream.println("\t-DappId=<appId> -DappKey=<appKey>");
        printStream.println("\t-DuserId=<userId> -userKey=<userKey>");
        printStream.println("\t-DoutputFolder=<outputFolder>");
    }

    private static void assertDataSetIdExists(ID2LUserContext iD2lUserContext,
            String dataSetId) throws ClientProtocolException, IOException {
        System.err.println(
                format("Verifying that the data set is available for export-job creation [dataSetId = %s]...",
                        dataSetId));

        makeGetRequest(iD2lUserContext,
                format("/d2l/api/lp/1.13/dataExport/list/%s", dataSetId),
                e -> format(
                        "cannot find the specified data set [dataSetId = %s, statusCode = %d]",
                        dataSetId, e));

        System.err.println(
                format("Verified that the data set is available for export-job creation [dataSetId = %s].",
                        dataSetId));
    }

    private static HttpResponse makeGetRequest(ID2LUserContext iD2lUserContext,
            String uri, ErrorMessageFactory errorMessageFactory)
                    throws ClientProtocolException, IOException {

        HttpResponse response = Get(
                iD2lUserContext.createAuthenticatedUri(uri, "GET")).execute()
                        .returnResponse();

        assertOkResponse(response, errorMessageFactory);

        return response;
    }

    private static HttpResponse makePostRequest(ID2LUserContext iD2lUserContext,
            String uri, String bodyString,
            ErrorMessageFactory errorMessageFactory)
                    throws ClientProtocolException, IOException {

        HttpResponse response = Post(
                iD2lUserContext.createAuthenticatedUri(uri, "POST"))
                        .bodyString(bodyString, APPLICATION_JSON).execute()
                        .returnResponse();

        assertOkResponse(response, errorMessageFactory);

        return response;
    }

    private static void assertOkResponse(HttpResponse response,
            ErrorMessageFactory errorMessageFactory) {
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != SC_OK) {
            die(errorMessageFactory.getErrorMessage(statusCode));
        }
    }

    private static String getStartDate(int minute) {
        return dates[minute % (dates.length - 1)];
    }

    private static String getEndDate(int minute) {
        return dates[(minute % (dates.length - 1)) + 1];
    }

    private static String submitExportJob(ID2LUserContext iD2lUserContext,
            String dataSetId, String startDate, String endDate)
                    throws ClientProtocolException, IOException {
        System.err.println(
                format("Creating the export job [dataSetId = %s, startDate = %s, endDate = %s]...",
                        dataSetId, startDate, endDate));

        String bodyString = format(
                "{\"DataSetId\": \"%s\", \"Filters\": [{\"Name\": \"startDate\", \"Value\": \"%s\"}, {\"Name\": \"endDate\", \"Value\": \"%s\"}]}",
                dataSetId, startDate, endDate);

        String exportJobId = createReader(makePostRequest(iD2lUserContext,
                "/d2l/api/lp/1.13/dataExport/create", bodyString,
                e -> format(
                        "cannot create job [requestBody = %s, statusCode = %d]",
                        bodyString, e)).getEntity().getContent()).readObject()
                                .getString("ExportJobId");

        System.err.println(
                format("Created the export job [dataSetId = %s, startDate = %s, endDate = %s, exportJobId = %s].",
                        dataSetId, startDate, endDate, exportJobId));

        return exportJobId;
    }

    private static void pollForCompletedExportJob(
            ID2LUserContext iD2lUserContext, String exportJobId)
                    throws ClientProtocolException, IOException {
        int status = doPollForCompletedExportJob(iD2lUserContext, exportJobId);

        while (isStatusValidButIncomplete(status)) {
            try {
                sleep(10000);
            } catch (InterruptedException e) {
            }

            status = doPollForCompletedExportJob(iD2lUserContext, exportJobId);
        }
    }

    private static boolean isStatusValidButIncomplete(int status) {
        return status == 0 || status == 1;
    }

    private static int doPollForCompletedExportJob(
            ID2LUserContext iD2lUserContext, String exportJobId)
                    throws IOException, ClientProtocolException {
        System.err.println(
                format("Checking the export-job status [exportJobId = %s]...",
                        exportJobId));

        int status = createReader(makeGetRequest(iD2lUserContext,
                format("/d2l/api/lp/1.13/dataExport/jobs/%s",
                        exportJobId),
                e -> format(
                        "cannot check the export-job status [exportJobId = %s, statusCode = %d]",
                        exportJobId, e)).getEntity().getContent()).readObject()
                                .getInt("Status");

        assertValidStatus(exportJobId, status);

        System.err.println(
                format("Checked the export-job status [exportJobId = %s, status = %d].",
                        exportJobId, status));

        return status;
    }

    private static void assertValidStatus(String exportJobId, int status) {
        if (status == 3) {
            die(format("the export job failed [exportJobId = %s, status = %d",
                    exportJobId, status));
        } else if (status == 4) {
            die(format(
                    "the results of the export job have been deleted [exportJobId = %s, status = %d",
                    exportJobId, status));
        }
    }

    private static void downloadCompletedExportJob(
            ID2LUserContext iD2lUserContext, String exportJobId,
            String outputFolder) throws ClientProtocolException, IOException {
        System.err.println(
                format("Downloading the export job [exportJobId = %s, outputFolder = %s]...",
                        exportJobId, outputFolder));

        File tempFile = createTempFile(exportJobId, ".zip",
                new File(outputFolder));

        try (FileOutputStream fileOutputStream = new FileOutputStream(
                tempFile)) {

            makeGetRequest(iD2lUserContext,
                    format("/d2l/api/lp/1.13/dataExport/download/%s",
                            exportJobId),
                    e -> format(
                            "cannot download the export job [exportJobId = %s, statusCode = %d]",
                            exportJobId, e)).getEntity()
                                    .writeTo(fileOutputStream);
        }

        System.err.println(
                format("Downloaded the export job [exportJobId = %s, outputPath = %s].",
                        exportJobId, tempFile.getCanonicalPath()));
    }
}

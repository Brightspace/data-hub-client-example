package com.d2l;

import static com.d2lvalence.idkeyauth.AuthenticationSecurityFactory.createSecurityContext;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static javax.json.Json.createReader;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;

import com.d2lvalence.idkeyauth.ID2LUserContext;

public class Main {
    public static void main(String[] args)
            throws URISyntaxException, ClientProtocolException, IOException {

        String hostUrl = getProperty("hostUrl");
        String dataSetId = getProperty("dataSetId");
        String appId = getProperty("appId");
        String appKey = getProperty("appKey");
        String userId = getProperty("userId");
        String userKey = getProperty("userKey");

        validateInput(hostUrl, dataSetId, appId, appKey, userId, userKey);

        ID2LUserContext iD2lUserContext = createSecurityContext(appId, appKey,
                hostUrl).createUserContext(userId, userKey);

        validateDataSetId(iD2lUserContext, dataSetId);

        String startDate = getStartDate();
        String endDate = getEndDate();
        String exportJobId = submitExportJob(iD2lUserContext, dataSetId,
                startDate, endDate);

        pollForCompletedExportJob(iD2lUserContext, exportJobId);

        downloadCompletedExportJob(iD2lUserContext, exportJobId);
    }

    private static void downloadCompletedExportJob(
            ID2LUserContext iD2lUserContext, String exportJobId)
                    throws ClientProtocolException, IOException {
        System.err.println(
                format("Downloading the export-job [exportJobId = %s]...",
                        exportJobId));

        HttpResponse httpResponse = Get(iD2lUserContext.createAuthenticatedUri(
                format("/d2l/api/lp/unstable/dataExport/download/%s",
                        exportJobId),
                "GET")).execute().returnResponse();

        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode != SC_OK) {
            die(format(
                    "cannot download the export job [exportJobId = %s, statusCode = %d]",
                    exportJobId, statusCode));
        }
    }

    private static void pollForCompletedExportJob(
            ID2LUserContext iD2lUserContext, String exportJobId)
                    throws ClientProtocolException, IOException {
        int status = doPollForCompletedExportJob(iD2lUserContext, exportJobId);

        while (status == 0 || status == 1) {
            status = doPollForCompletedExportJob(iD2lUserContext, exportJobId);
        }
    }

    private static int doPollForCompletedExportJob(
            ID2LUserContext iD2lUserContext, String exportJobId)
                    throws IOException, ClientProtocolException {
        System.err.println(
                format("Checking the export-job status [exportJobId = %s]...",
                        exportJobId));

        HttpResponse httpResponse = Get(iD2lUserContext.createAuthenticatedUri(
                format("/d2l/api/lp/unstable/dataExport/status/%d",
                        exportJobId),
                "GET")).execute().returnResponse();

        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode != SC_OK) {
            die(format(
                    "cannot check the export-job status [exportJobId = %s, statusCode = %d]",
                    exportJobId, statusCode));
        }

        int status = parseInt(
                createReader(httpResponse.getEntity().getContent()).readObject()
                        .getString("Status"));

        if (status == 3) {
            die(format("the export job failed [exportJobId = %s, status = %d",
                    exportJobId, status));
        } else if (status == 4) {
            die(format(
                    "the results of the export job have been deleted [exportJobId = %s, status = %d",
                    exportJobId, status));
        }

        System.err.println(
                format("Checked the export-job status [exportJobId = %s, status = %d].",
                        exportJobId, status));
        return status;
    }

    private static String submitExportJob(ID2LUserContext iD2lUserContext,
            String dataSetId, String startDate, String endDate)
                    throws ClientProtocolException, IOException {
        System.err.println(
                format("Creating the export-job [dataSetId = %s, startDate = %s, endDate = %s]...",
                        dataSetId, startDate, endDate));

        String bodyString = format(
                "{\"DataSetId\": \"%s\", \"Filters\": [{\"Name\": \"startDate\", \"Value\": \"%s\"}, {\"Name\": \"endDate\", \"Value\": \"%s\"}]}",
                "ba58a5a3-6d57-4720-9d25-02361f5049ae", startDate, endDate);

        HttpResponse httpResponse = Post(iD2lUserContext.createAuthenticatedUri(
                "/d2l/api/lp/unstable/dataExport/create", "POST"))
                        .bodyString(bodyString, APPLICATION_JSON).execute()
                        .returnResponse();

        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode != SC_OK) {
            die(format(
                    "cannot create job [dataSetId = %s, startDate = %s, endDate = %s, statusCode = %d]",
                    dataSetId, startDate, endDate, statusCode));
        }

        String exportJobId = createReader(httpResponse.getEntity().getContent())
                .readObject().getString("ExportJobId");

        System.err.println(
                format("Created the export-job [dataSetId = %s, startDate = %s, endDate = %s, exportJobId = %s].",
                        dataSetId, startDate, endDate, exportJobId));

        return exportJobId;
    }

    private static void validateDataSetId(ID2LUserContext iD2lUserContext,
            String dataSetId) throws ClientProtocolException, IOException {
        System.err.println(
                format("Verifying that the data set is available for export-job creation [dataSetId = %s]...",
                        dataSetId));

        int statusCode = Get(iD2lUserContext.createAuthenticatedUri(
                "/d2l/api/lp/unstable/dataExport/list", "GET")).execute()
                        .returnResponse().getStatusLine().getStatusCode();

        if (statusCode != SC_OK) {
            die(format(
                    "cannot find the specified data set [dataSetId = %s, statusCode = %d]",
                    dataSetId, statusCode));
        }

        System.err.println(
                format("Verified that the data set is available for export-job creation [dataSetId = %s].",
                        dataSetId));
    }

    private static void validateInput(String... input) {
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
        printStream.println("\t-DhostUrl=<hostUrl>");
        printStream.println(
                "\t-DdataSetId=<reportId> -DstartDate=<startDate> -DendDate=<endDate>");
        printStream.println("\t-DappId=<appId> -DappKey=<appKey>");
        printStream.println("\t-DuserId=<userId> -userKey=<userKey>");
    }

    private static String getStartDate() {
        return null;
    }

    private static String getEndDate() {
        return null;
    }
}

/*
    Copyright 2016 D2L Corporation

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */

package com.d2l;

import static java.io.File.createTempFile;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;
import static javax.json.Json.createReader;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Form;

import javax.json.JsonObject;

public class Main {

    @FunctionalInterface
    private static interface ErrorMessageFactory {
        String getErrorMessage(int errorCode);
    }

    private static final DateTimeFormatter dtFormatter = DateTimeFormatter.ISO_INSTANT;
    private static final Calendar now = Calendar.getInstance();

    public static void main(String[] args)
            throws URISyntaxException, IOException, InterruptedException {

        /* Data Hub related properties */
        String hostUrl = getProperty("hostUrl");
        String dataSetId = getProperty(
                "dataSetId",
                "c1bf7603-669f-4bef-8cf4-651b914c4678");
        String outputFolder = getProperty("outputFolder");

        /* OAuth 2.0 related properties */
        // Development purposes only; the default value should always suffice
        String tokenEndpoint = getProperty(
                "tokenEndpoint",
                "https://auth.brightspace.com/core/connect/token"
        );

        String clientId = getProperty("clientId");
        String clientSecret = getProperty("clientSecret");

        String refreshTokenFile = getProperty("refreshTokenFile");

        /* Pre-condition checks */
        assertAllArgumentsSpecified(hostUrl, dataSetId, clientId,
                clientSecret, outputFolder, refreshTokenFile);

        /* Retrieve a valid refresh token and use it to obtain a new access token */
        Path refreshTokenPath = Paths.get(refreshTokenFile);
        String oldRefreshToken = Files.readAllLines(refreshTokenPath).get(0);

        String authHeaderValue = getClientAuthHeaderValue(clientId, clientSecret);

        List<NameValuePair> payload = Form.form()
                .add("grant_type", "refresh_token")
                .add("refresh_token", oldRefreshToken)
                .build();

        HttpResponse response = Post(tokenEndpoint)
                .setHeader(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .bodyForm(payload)
                .execute()
                .returnResponse();

        assertOkResponse(
                response,
                e -> "Could not get access token"
        );

        JsonObject responseJson = createReader(
                response
                        .getEntity()
                        .getContent()
        )
        .readObject();

        String accessToken = responseJson.getString("access_token");

        // Refresh token are one-time use
        // The token endpoint provides a new refresh token that we should store for future requests
        String newRefreshToken = responseJson.getString("refresh_token");
        Files.write(refreshTokenPath, newRefreshToken.getBytes());

        /* Kick off a Data Hub export */
        assertDataSetIdExists(accessToken, hostUrl, dataSetId);

        String startDate = getStartDate();
        String endDate = getEndDate();
        String exportJobId = submitExportJob(accessToken, hostUrl, dataSetId,
                startDate, endDate);

        pollForCompletedExportJob(accessToken, hostUrl, exportJobId);

        downloadCompletedExportJob(accessToken, hostUrl, exportJobId, outputFolder);
    }

    /* General helper methods */
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
        printStream.println("\t-DclientId=<clientId> -DclientSecret=<clientSecret>");
        printStream.println("\t-DefreshTokenFile=<refreshTokenFile>");
        printStream.println("\t-DoutputFolder=<outputFolder>");
    }

    /* HTTP helper methods */
    private static void assertOkResponse(HttpResponse response,
                                         ErrorMessageFactory errorMessageFactory) {
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != SC_OK) {
            die(errorMessageFactory.getErrorMessage(statusCode));
        }
    }

    private static HttpResponse makeGetRequest(String accessToken,
                                               String uri, ErrorMessageFactory errorMessageFactory)
            throws IOException {

        HttpResponse response = Get(uri)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .execute()
                .returnResponse();

        assertOkResponse(response, errorMessageFactory);

        return response;
    }

    private static HttpResponse makePostRequest(String accessToken,
                                                String uri, String bodyString,
                                                ErrorMessageFactory errorMessageFactory)
            throws IOException {

        HttpResponse response = Post(uri)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .bodyString(bodyString, APPLICATION_JSON).execute()
                .returnResponse();

        assertOkResponse(response, errorMessageFactory);

        return response;
    }

    /* OAuth 2.0 helper methods */
    private static String getClientAuthHeaderValue(
            String clientId,
            String clientSecret
    ) {
        // Based on http://www.baeldung.com/httpclient-4-basic-authentication
        String auth = clientId + ":" + clientSecret;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("ISO-8859-1")));
        return "Basic " + new String(encodedAuth);
    }

    /* Data Hub helper methods */
    private static void assertDataSetIdExists(String accessToken, String hostUrl,
            String dataSetId) throws IOException {
        System.err.println(
                format("Verifying that the data set is available for export-job creation [dataSetId = %s]...",
                        dataSetId));

        makeGetRequest(accessToken,
                format("%s/d2l/api/lp/1.13/dataExport/list/%s", hostUrl, dataSetId),
                e -> format(
                        "cannot find the specified data set [dataSetId = %s, statusCode = %d]",
                        hostUrl, dataSetId, e));

        System.err.println(
                format("Verified that the data set is available for export-job creation [dataSetId = %s].",
                        dataSetId));
    }

    private static String getStartDate() {
        Calendar yesterdayAtMidnightUtc = Calendar.getInstance();
        yesterdayAtMidnightUtc.set(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH) - 1,
                0,  // hour of day
                0,  // minute
                0   // second
        );

        return dtFormatter.format(yesterdayAtMidnightUtc.toInstant());
    }

    private static String getEndDate() {
        Calendar todayAtMidnightUtc = Calendar.getInstance();
        todayAtMidnightUtc.set(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH),
                0,  // hour of day
                0,  // minute
                0   // second
        );

        return dtFormatter.format(todayAtMidnightUtc.toInstant());
    }

    private static String submitExportJob(String accessToken, String hostUrl,
            String dataSetId, String startDate, String endDate)
                    throws IOException {
        System.err.println(
                format("Creating the export job [dataSetId = %s, startDate = %s, endDate = %s]...",
                        dataSetId, startDate, endDate));

        String bodyString = format(
                "{\"DataSetId\": \"%s\", \"Filters\": [{\"Name\": \"startDate\", \"Value\": \"%s\"}, {\"Name\": \"endDate\", \"Value\": \"%s\"}]}",
                dataSetId, startDate, endDate);

        String exportJobId = createReader(makePostRequest(accessToken,
                format("%s/d2l/api/lp/1.13/dataExport/create", hostUrl), bodyString,
                e -> format(
                        "cannot create job [requestBody = %s, statusCode = %d]",
                        hostUrl, bodyString, e)).getEntity().getContent()).readObject()
                                .getString("ExportJobId");

        System.err.println(
                format("Created the export job [dataSetId = %s, startDate = %s, endDate = %s, exportJobId = %s].",
                        dataSetId, startDate, endDate, exportJobId));

        return exportJobId;
    }

    private static void pollForCompletedExportJob(
            String accessToken, String hostUrl, String exportJobId)
                    throws IOException {
        int status = doPollForCompletedExportJob(accessToken, hostUrl, exportJobId);

        while (isStatusValidButIncomplete(status)) {
            try {
                sleep(10000);
            } catch (InterruptedException e) {
            }

            status = doPollForCompletedExportJob(accessToken, hostUrl, exportJobId);
        }
    }

    private static boolean isStatusValidButIncomplete(int status) {
        return status == 0 || status == 1;
    }

    private static int doPollForCompletedExportJob(
            String accessToken, String hostUrl, String exportJobId)
                    throws IOException {
        System.err.println(
                format("Checking the export-job status [exportJobId = %s]...",
                        exportJobId));

        int status = createReader(makeGetRequest(accessToken,
                format("%s/d2l/api/lp/1.13/dataExport/jobs/%s",
                        hostUrl, exportJobId),
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
            String accessToken, String hostUrl, String exportJobId,
            String outputFolder) throws IOException {
        System.err.println(
                format("Downloading the export job [exportJobId = %s, outputFolder = %s]...",
                        exportJobId, outputFolder));

        File tempFile = createTempFile(exportJobId, ".zip",
                new File(outputFolder));

        try (FileOutputStream fileOutputStream = new FileOutputStream(
                tempFile)) {

            makeGetRequest(accessToken,
                    format("%s/d2l/api/lp/1.13/dataExport/download/%s",
                            hostUrl, exportJobId),
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

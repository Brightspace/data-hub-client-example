package com.d2l;

import static com.d2lvalence.idkeyauth.AuthenticationSecurityFactory.createSecurityContext;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.client.fluent.Request.Post;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Calendar;

import org.apache.http.client.fluent.Request;

import com.d2lvalence.idkeyauth.ID2LUserContext;

public class Main {

    private static String[] dates = {
            "2016-05-31T09:00:00.0000000Z",
            "2016-05-31T09:00:23.4560000Z",
            "2016-05-31T09:01:00.0000000Z",
            "2016-05-31T09:02:45.6780000Z"
    };

    public static void main(String[] args)
            throws URISyntaxException, IOException, InterruptedException {

        String hostUrl = getProperty("hostUrl");
        String appId = getProperty("appId");
        String appKey = getProperty("appKey");
        String userId = getProperty("userId");
        String userKey = getProperty("userKey");
        String outputFolder = getProperty("outputFolder");
        String dataSetId = getProperty("dataSetId", "c1bf7603-669f-4bef-8cf4-651b914c4678");

        validateInput(hostUrl, appId, appKey, userId, userKey);

        Calendar now = Calendar.getInstance();
        int currentMinute = now.get(Calendar.MINUTE);

        String startDate = getStartDate(currentMinute);
        String endDate = getEndDate(currentMinute);
        System.out.println("StartDate: " + startDate + " | EndDate: " + endDate);

        ID2LUserContext id2lUserContext = createSecurityContext(appId, appKey, hostUrl).createUserContext(userId, userKey);

        String bodyString = format(
                "{\"DataSetId\": \"%s\"," +
                        "\"Filters\": [" +
                        "{\"Name\": \"startDate\", \"Value\": \"%s\"}, " +
                        "{\"Name\": \"endDate\", \"Value\": \"%s\"}]}",
                dataSetId, startDate, endDate);
        System.out.println("BodyString: " + bodyString);

        String jobId = Post(id2lUserContext.createAuthenticatedUri("/d2l/api/lp/unstable/dataExport/create", "POST"))
                .bodyString(bodyString, APPLICATION_JSON).execute()
                .returnContent()
                .asString()
                .replace("\"", "")
                .split(" ")[1];
        System.out.println("JobID: " + jobId);

        String status = "0";
        while(!status.equals("2")) {
            Thread.sleep(1000);
            status = Request
                    .Get(id2lUserContext.createAuthenticatedUri(format("/d2l/api/lp/unstable/dataExport/status/%s", jobId), "GET"))
                    .execute()
                    .returnContent()
                    .asString()
                    .replace("\"", "")
                    .split(" ")[1];
        }

        byte[] download = Request
                .Get(id2lUserContext.createAuthenticatedUri(format("/d2l/api/lp/unstable/dataExport/download/%s",  jobId), "GET"))
                .execute()
                .returnContent()
                .asBytes();

        String ouputPath = outputFolder + File.separator + now.getTimeInMillis()+".zip";
        FileOutputStream fos = new FileOutputStream(ouputPath);
        fos.write(download);
        fos.close();
        System.out.println("Downloaded to: " + ouputPath);
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
        printStream.println("\t-DdataSetId=<reportId> -DstartDate=<startDate> -DendDate=<endDate>");
        printStream.println("\t-DappId=<appId> -DappKey=<appKey>");
        printStream.println("\t-DuserId=<userId> -userKey=<userKey>");
        printStream.println("\t-DoutputFolder=<outputFolder>");
    }

    private static String getStartDate(int minute) {
        return dates[minute % (dates.length - 1)];
    }

    private static String getEndDate(int minute) {
        return dates[(minute % (dates.length - 1)) + 1];
    }
}

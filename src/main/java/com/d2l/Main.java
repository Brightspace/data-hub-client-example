package com.d2l;

import static com.d2lvalence.idkeyauth.AuthenticationSecurityFactory.createSecurityContext;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.client.fluent.Request.Post;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;

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
        
        validateInput(hostUrl, appId, appKey, userId, userKey);
        
        
        
        ID2LUserContext id2lUserContext = createSecurityContext(appId, appKey,
                hostUrl).createUserContext(userId, userKey);

        // JsonReader jsonReader = Json.
//        String bodyString = format(
//                "{\"DataSetId\": \"%s\", \"Filters\": [{\"Name\": \"startDate\", \"Value\": \"2016-05-29\"}, {\"Name\": \"endDate\", \"Value\": \"2016-05-30\"}]}",
//                "ba58a5a3-6d57-4720-9d25-02361f5049ae");
//        Post(id2lUserContext.createAuthenticatedUri(
//                "/d2l/api/lp/unstable/dataExport/create", "POST"))
//                        .bodyString(bodyString, APPLICATION_JSON).execute()
//                        .returnContent().asString();
//        System.out.println(Request
//                .Get(id2lUserContext.createAuthenticatedUri(
//                        "/d2l/api/lp/unstable/dataExport/list", "GET"))
//                .execute().returnContent().asString());
//        System.out.println(Request.Get(id2lUserContext.createAuthenticatedUri(
//                "/d2l/api/lp/unstable/dataExport/status/aaa6ae5c-fca3-4513-916c-caf1f2cff996",
//                "GET")).execute().returnContent().asString());
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
    }
    
    private static String getStartDate() {
        return null;
    }
    
    private static String getEndDate() {
        return null;
    }
}

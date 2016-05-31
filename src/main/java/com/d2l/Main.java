package com.d2l;

import static java.lang.System.getProperty;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;

import com.d2lvalence.idkeyauth.AuthenticationSecurityFactory;
import com.d2lvalence.idkeyauth.ID2LAppContext;
import com.d2lvalence.idkeyauth.ID2LUserContext;

/**
 * Hello world!
 *
 */
public class Main {
    public static void main(String[] args)
            throws URISyntaxException, ClientProtocolException, IOException {
        ID2LAppContext id2lAppContext = AuthenticationSecurityFactory
                .createSecurityContext(getProperty("appId"),
                        getProperty("appKey"), getProperty("leUrl"));
        ID2LUserContext id2lUserContext = id2lAppContext.createUserContext(
                getProperty("userId"), getProperty("userKey"));

        // System.out.println(Request
        // .Get(id2lUserContext.createAuthenticatedUri(
        // "/d2l/api/lp/unstable/dataExport/list", "GET"))
        // .execute().returnContent().asString());
        // System.out.println(Request.Get(id2lUserContext.createAuthenticatedUri(
        // "/d2l/api/lp/unstable/dataExport/list/c1bf7603-669f-4bef-8cf4-651b914c4678",
        // "GET")).execute().returnContent().asString());
        // String bodyString = "{`DataSetId`:
        // `c1bf7603-669f-4bef-8cf4-651b914c4678`, `Filters`: [{`Name`:
        // `startDate`, `Value`: `2016-05-29`}, {`Name`: `endDate`, `Value`:
        // `2016-05-30`}]}"
        // .replace('`', '"');
        // System.out.println(bodyString);
        // System.out.println(Request
        // .Post(id2lUserContext.createAuthenticatedUri(
        // "/d2l/api/lp/unstable/dataExport/create", "POST"))
        // .bodyString(bodyString, APPLICATION_JSON)
        // .execute().returnContent().asString());
        System.out.println(Request
                .Get(id2lUserContext.createAuthenticatedUri(
                        "/d2l/api/lp/unstable/dataExport/jobs", "GET"))
                .execute().returnContent().asString());
        System.out.println(Request.Get(id2lUserContext.createAuthenticatedUri(
                "/d2l/api/lp/unstable/dataExport/status/d9b5d1c1-5f6f-4cb3-bb40-44c7ed18da0e",
                "GET")).execute().returnContent().asString());
    }
}

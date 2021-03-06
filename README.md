# Data Hub Client Example

This is a demonstration of how to retrieve data from Brightspace via the Data Hub APIs.

This example retrieves data for the *Enrollments and Withdrawals* data set. The [Brightspace API reference](http://docs.valence.desire2learn.com/reference.html) will document the Data Export route you can use to discover the set of supported data sets. Additionally, we have published an article on the [Brightspace Developer Community blog](https://community.brightspace.com/s/article/ka1610000000p9gAAA/Getting-Started-with-Data-Hub) to provide a more in-depth walkthrough about this new feature.

Note: this client assumes a valid OAuth 2.0 refresh token has already been obtained, and does not initiate the [OAuth 2.0 authorization flow](https://tools.ietf.org/html/rfc6749#section-4). An article on the [Brightspace Developer Community blog](https://community.brightspace.com/s/article/ka1610000000pYqAAI/How-to-obtain-an-OAuth-2-0-Refresh-Token) is available to provide steps on how to obtain a refresh token.

## Building

This project uses [Maven](https://maven.apache.org/). To build it, run `mvn package` in the parent directory.

## Running

This application requires the following system properties to be set:

**hostUrl**

* The URL to your Brightspace instance
* e.g. `https://myschool.brightspace.com`

**clientId**

* The OAuth 2.0 client ID (available from the Brightspace OAuth 2.0 registration page)

**clientSecret**

* The OAuth 2.0 client secret (available from the Brightspace OAuth 2.0 registration page)

**refreshTokenFile**

* The file used to store a valid OAuth 2.0 refresh token
* When running this for the first time, a valid refresh token needs to already exist in this file
* Ensure the user running the process has read and write access to this file

**outputFolder**

* The folder to save the data set into
* Ensure the user running the process has write access to this folder

### Example

	java -DhostUrl=http://myschool.brightspace.com/ -DclientId=theClientId -DclientSecret=theClientSecret -DoutputFolder=/output -DrefreshTokenFile=refreshToken.txt -jar data-hub-client-example-1.0-SNAPSHOT.jar


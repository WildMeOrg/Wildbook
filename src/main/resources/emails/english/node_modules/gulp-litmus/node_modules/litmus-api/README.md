#litmus-api
Provides methods that correlate with the [Litmus Customer API](http://docs.litmus.com/w/page/18056603/Customer%20API%20documentation). All methods return a promise thanks to the use of the [Bluebird](https://github.com/petkaantonov/bluebird) promise library. Be sure to read the [Bluebird API docs](https://github.com/petkaantonov/bluebird/blob/master/API.md) to learn about the different methods that can be used. 

## Example
```js
var Litmus = require('litmus-api');

var api = new Litmus({
    username: 'username',
    password: 'password',
    url: 'https://company.litmus.com'
});

api.getTests()
    .then(function(data){
        var response = data[0];
        var body = data[1];
        
        console.log(response);
        console.log(body);
    });
```

## Constructor

####`new Litmus(options)`

`options` - required object that contains your Litmus credentials
`options.username` - Litmus username    
`options.password` - Litmus password   
`options.url` - URL to your Litmus account 

Example
```js
var options = {
    username: 'username',
    password: 'password',
    url: 'https://company.litmus.com'
};
var api = new Litmus(options);
``` 

## Methods

####`api.getTests()`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-Documentation:-tests](http://docs.litmus.com/Customer-API-Documentation:-tests)  

Returns 100 results of the most recent tests 

<hr>

####`api.getTest(testId)`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-tests-show](http://docs.litmus.com/Customer-API-documentation:-tests-show)  
`testId` - id of test  

Returns the details of a single test by passing id of test

<hr>

####`api.updateTest(testId, body)`
**HTTP Method:** `PUT`  
**Reference:** [http://docs.litmus.com/Customer-API-Documentation:-tests-update](http://docs.litmus.com/Customer-API-Documentation:-tests-update)  
`testId` - id of test  
`body` - XML data

Updates a test in your account. This is used for publishing results publicly or changing a test's title.

**Request body example:**  
```xml
<?xml version="1.0" encoding="UTF-8"?>
<test_set>
  <public_sharing>true</public_sharing>
  <name>Newsletter example</name>
</test_set> 
```

<hr>

####`api.deleteTest(testId)`
**HTTP Method:** `DELETE`  
**Reference:** [http://docs.litmus.com/Customer-API-Documentation:-tests-destroy](http://docs.litmus.com/Customer-API-Documentation:-tests-destroy)  
`testId` - id of test  

Deletes a single test

<hr>


####`api.getVersions(testId)`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-versions](http://docs.litmus.com/Customer-API-documentation:-versions)  
`testId` - id of test  

Returns all versions for a specified test

<hr>

####`api.getVersion(testId, version)`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-versions-show](http://docs.litmus.com/Customer-API-documentation:-versions-show)  
`testId` - id of test  
`version` - version number of test

Returns details of a single version for a particular test

<hr>

####`api.createVersion(testId)`
**HTTP Method:** `POST`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-versions-create](http://docs.litmus.com/Customer-API-documentation:-versions-create)  
`testId` - id of test  

Creates a new version of a test.  
Creating a new version of a web page test will re-test the same URL immediately. Creating a new version of an email test will return a new email address in the `url_or_guid` field and the received field will be `false`. You'll need to send an email to that address for received to become `true` and your screenshots to be generated. The `location` field of the headers returned will include a link to the newly created test.

<hr>

####`api.pollVersion(testId, version)`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-versions-poll](http://docs.litmus.com/Customer-API-documentation:-versions-poll)  
`testId` - id of test  
`version` - version of test

To reduce the strain on the Litmus servers, and to reduce the bandwidth you use to check for test completion, there is a special poll method that can be used. The XML document returned will give an indication as to the status of each result. You may want to wait for every result to complete, or you may wish to return each result as it completes. You can check the status of the poll method and fetch the whole test version when the state for a particular result changes.

<hr>

####`api.getResults(testId, version)`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-results](http://docs.litmus.com/Customer-API-documentation:-results)  
`testId` - id of test  
`version` - version of test

Retrieves the complete collection of results for a particular test and version

<hr>

####`api.getResult(testId, version, resultId)`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-results-show](http://docs.litmus.com/Customer-API-documentation:-results-show)  
`testId` - id of test  
`version` - version of test  
`resultId` - id of a result in a collection of results

Used to retrieve details of a single result, useful when used in conjunction with the versions/poll method while waiting for individual results to complete.

<hr>

####`api.updateResult(testId, version, resultId, body)`
**HTTP Method:** `PUT`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-results-update](http://docs.litmus.com/Customer-API-documentation:-results-update)  
`testId` - id of test  
`version` - version of test  
`resultId` - id of a result in a collection of results
`body` - XML data to send

This method is used to update the properties of a result. Currently the only operation supported by this is to set the compatibility state of a result (whether it appears with a green tick or red cross in the Litmus web interface). This is set via the `<check_state>` parameter which support either `ticked`, `crossed` or `nostate` as valid values. A result which returns `nil` for `<check_state>` is considered to be `nostate`.

**Request body example**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <check_state>ticked</check_state>
</result>
```

<hr>

####`api.retestResult(testId, version, resultId)`
**HTTP Method:** `POST`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-results-retest](http://docs.litmus.com/Customer-API-documentation:-results-retest)  
`testId` - id of test  
`version` - version of test  
`resultId` - id of a result in a collection of results  

Triggers a retest of just this client. Behaviour differs between page and email tests. For email tests we simply reuse the email source that was sent previously, this means it is best for just attempting to retest if an error occurred with a particular client. For page tests, this will revisit the url supplied when you started the test, meaning that any changes since the original test will be captured. Normally retesting like this is just best when an error occurs, if you've made changes to your email or page then testing by creating a new version is best.

<hr>

####`api.createEmailTest(body)`
**HTTP Method:** `POST`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-emails-create](http://docs.litmus.com/Customer-API-documentation:-emails-create)  
`body` - XML data to send  

You have two ways of sending a test to Litmus:  
**1. Sending an email**  
Using this method, there are a few steps to the process of creating an email test and displaying it to the end user, the advantage is that it is that your email will arrive at Litmus in the same way that it will arrive in your customers inbox, via SMTP.  

1. Create the new email test, specifying the email clients you wish to test it on.
2. Record the new test's `<id>`, and its `<url_or_guid>`. 
3. Send the email that is to be tested, to the address specified in the `<url_or_guid>` field.
4. Poll Litmus on the test's progress. The received field will be set to true when the email has been received by our system. 
5. Once the test is complete, record the URLs to the result screenshots, and present these to your user.   

Request body example: 
```xml
<?xml version="1.0"?>
<test_set>
  <applications type="array">
    <application>
      <code>hotmail</code>
    </application>
    <application>
      <code>gmail</code>
    </application>
    <application>
      <code>notes8</code>
    </application>
  </applications>
  <save_defaults>false</save_defaults>
  <use_defaults>false</use_defaults>
</test_set>
```

**2. Uploading HTML data**  
For this method, you simply supply your email body and subject as part of the XML request to the API. Simply change your request to look like this, everything else remains the same as the example above.

Request body example: 
```xml
<?xml version="1.0"?>
<test_set>
  <applications type="array">
    <application>
      <code>hotmail</code>
    </application>
    <application>
      <code>gmail</code>
    </application>
    <application>
      <code>notes8</code>
    </application>
  </applications>
  <save_defaults>false</save_defaults>
  <use_defaults>false</use_defaults>
  <email_source>
     <body><![CDATA[your-email-html-goes-here]]></body>
     <subject>My test email to Litmus</subject>
  </email_source>
</test_set>
```
<hr>

####`api.getEmailClients()`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-emails-clients](http://docs.litmus.com/Customer-API-documentation:-emails-clients)  

Returns a list of email clients available for testing.  

<hr>

####`api.createBrowserTest(body)`
**HTTP Method:** `POST`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-pages-create](http://docs.litmus.com/Customer-API-documentation:-pages-create)  
`body` - XML data to send  

Creates a new web page test in your account.  
This will create a new web page test, there are a few steps to the process of creating a test and displaying it to the end user:

1. Create the new test with the URL to the page you're testing, along with the browsers you wish to test it on. 
2. Record the new test's `<id>`. (Contained in response to previous POST)
3. Poll Litmus on the test's progress. 
4. Once the test is complete, record the URLs to the result screenshots, and present these to your user.  

Request body example: 
```xml
<?xml version="1.0" encoding="UTF-8"?>
<test_set>
  <applications type="array">
    <application>
      <code>saf2</code>
    </application>
    <application>
      <code>ie7</code>
    </application>
    <application>
      <code>ie6</code>
    </application>
  </applications>
  <url>http://google.com</url>
  <save_defaults>false</save_defaults>
  <use_defaults>false</use_defaults>
</test_set>
```

<hr>

####`api.getBrowserClients()`
**HTTP Method:** `GET`  
**Reference:** [http://docs.litmus.com/Customer-API-documentation:-pages-clients](http://docs.litmus.com/Customer-API-documentation:-pages-clients)  

Returns a list of web browsers available for testing  

















    var mail      = require('nodemailer').mail,
        cheerio   = require('cheerio'),
        builder   = require('xmlbuilder'),
        Table     = require('cli-table'),
        LitmusAPI = require('litmus-api'),
        chalk     = require('chalk'),
        _         = require('lodash');


function Litmus(options){
  this.options = options;
  this.initVars();
}

// Initialize variables
Litmus.prototype.initVars = function() {
  
  this.api = new LitmusAPI({
    username: this.options.username,
    password: this.options.password,
    url: this.options.url
  });
};


/**
* Calculate and get the average time for test to complete
*
* @param {String} body - xml body returned from response  
*
* @returns {String} average time in seconds/minutes 
* 
*/

Litmus.prototype.getAvgTime = function(body) {
 
  var $        = cheerio.load(body, { xmlMode: true }),
      avgTimes = $('average_time_to_process'),
      count    = 0;

  avgTimes.each(function(){
    count += +$(this).text();
  });

  return (count < 60) ? (count + ' secs') : (Math.round((count/avgTimes.length)/60) + ' mins');

};


/**
* Get the status of each result in a test
*
* @param {String} body - xml body returned from response  
*
* @returns {Object} map of delayed and unavailable clients based on status 
* 
*/

Litmus.prototype.getStatus = function(body) {
  
  var $ = cheerio.load(body, { xmlMode: true }),
      statuses = $('status'),
      delayed = [],
      unavailable = [],
      statusCode,
      application;

  statuses.each(function(){
    var $this = $(this);
    statusCode = +$this.text();
    application = $this.parent().children('application_long_name').text();

    if(statusCode === 1){ delayed.push(application); }

    if(statusCode === 2){ unavailable.push(application); }
  });

  return {
    delayed: delayed.join('\n'),
    unavailable: unavailable.join('\n')
  };

};


/**
* Creates a nice looking table on the command line that logs the
* average time it takes for a test to complete and delayed and unavailable clients
*
* @param {String} body - xml body returned from response  
* 
*/

Litmus.prototype.logStatusTable = function(body) {
 
  var table = new Table(),
      delayed = this.getStatus(body).delayed,
      unavailable = this.getStatus(body).unavailable,
      avgTime = this.getAvgTime(body),
      values = [];

  table.options.head = [chalk.bold('Avg. Time to Complete')];
  values.push(avgTime);

  if(delayed.length > 0){
    table.options.head.push(chalk.bold('Delayed'));
    values.push(delayed);
  }

  if(unavailable.length > 0){
    table.options.head.push(chalk.bold('Unavailable'));
    values.push(unavailable);
  }

  table.push(values);

  console.log(table.toString());
};


/**
* Logs headers of response once email is sent
*
* @param {Array} data - array of data returned from promise  
* 
*/

Litmus.prototype.logHeaders = function(data) {

  var res = data[0],
      body = data[1],
      headers = res.headers,
      status = parseFloat(headers.status, 10);

  Object.keys(headers).forEach(function(key){
    console.log(chalk.bold(key.toUpperCase()) + ': ' + headers[key]);
  });

  console.log('---------------------\n' + body); 

  if(status > 199 && status < 300){
    this.logSuccess('Test sent!');
    this.logStatusTable(body);
  } else {
    throw new Error(headers.status);
  }

};


/**
* Mail a new test using the test email Litmus provides in the <url_or_guid> tag
*
* @param {Array} data - array of data returned from promise  
* 
*/

Litmus.prototype.mailNewVersion = function(data) {

  var body = data[1];

  var $    = cheerio.load(body, { xmlMode: true }),
      guid = $('url_or_guid').text(); 

  mail({
      from: 'no-reply@test.com',
      to: guid,
      subject: this.title,
      text: '',
      html: this.html
  });
  this.logSuccess('New version sent!');
  this.logStatusTable(body);

};


/**
* Builds xml body
*
* @param {String} html - final html output  
* @param {String} title - title that will be used to name the Litmus test  
*
* @returns {Object} xml body for the request  
* 
*/

Litmus.prototype.getBuiltXml = function(html, title) {
  
  var xmlApplications = builder.create('applications').att('type', 'array');
  var item, xml;

  _.each(this.options.applications, function(app) {
    item = xmlApplications.ele('application');

    item.ele('code', app);
  });

  //Build Xml to send off, Join with Application XMl
  xml = builder.create('test_set')
    .importXMLBuilder(xmlApplications)
    .ele('save_defaults', 'false').up()
    .ele('use_defaults', 'false').up()
    .ele('email_source')
      .ele('body').dat(html).up()
      .ele('subject', title)
    .end({pretty: true});

  return xml;
};


/**
* Grab the name of email and set id if it matches title/subject line
*
* @param {String} body - xml body of all tests  
*
* @returns {Object} a map with the id 
* 
*/

Litmus.prototype.getId = function(body) {
 
  var xml = body[1];
  var map = {};
  var $ = cheerio.load(xml, { xmlMode: true }),
      $allNameTags = $('name'),
      subjLine = this.title,
      $matchedName = $allNameTags.filter(function(){
        return $(this).text() === subjLine;
      });

  if($matchedName.length){
    map.id = $matchedName.eq(0).parent().children('id').text();
  }

  return map;
};



/**
* Get the results of a version before sending a test
*
* @param {Object} map - object map that contains the id passed
*
* @returns {Object} object that contains version results length
* 
*/

Litmus.prototype.getVersionResults = function(map){
  
  return this.api.getVersion(map.id, 1)
    .then(function(data){

      var body = data[1];
      var $ = cheerio.load(body, { xmlMode: true });
      var results = $('result');

      map.versionResults = +results.length;

      return map;
      
    });

};


/**
* Remove unsupported clients if found in the options.applications array
*
* @param {Object} map - object map
*
* @returns {Object} object that contains latestTest, id and versionResults
* 
*/

Litmus.prototype.removeClients = function(map){
  
  return this.api.getEmailClients()
    .bind(this)
    .then(function(data){

      var body      = data[1],
          $         = cheerio.load(body, { xmlMode: true }),
          appCode   = $('application_code'),
          clients   = [],
          clientMap = {};

      var appName, appLongName;
      
      // Create map
      appCode.each(function(){
        
        var $this = $(this);
        appName = $this.text();
        appLongName = $this.parent().children('application_long_name').text();

        clientMap[appName] = appLongName;

      });
      
      // Remove unsupported applications
      this.options.applications.forEach(function(app){
        
        if(!clientMap[app]){
          this.logWarn('WARNING: ' + app + ' not supported');
        }else{
          clients.push(app);
        }

      }, this);

      // New array with unsupported applications removed
      this.options.applications = clients;

      return map;
    });
};


/**
* Send a new version if id is availabe otherwise send a new test
*
* @param {Object} data - object map that contains the id passed
*
* @returns {Object} a promise
* 
*/

Litmus.prototype.sendTest = function(map) {

  var body = this.getBuiltXml(this.html, this.title);
  var appLength = this.options.applications.length;

  if( (map.id) && (appLength === map.versionResults) ){
    this.log(chalk.bold('Sending new version: ') + this.title);
    
    return this.api.createVersion(map.id)
      .bind(this)
      .then(this.mailNewVersion);
  }else{
    this.log(chalk.bold('Sending new test: ') + this.title);
    
    return this.api.createEmailTest(body)
      .bind(this)
      .then(this.logHeaders);
  }
};


/**
* Starts the initialization
*
* @param {String} html - final html output
* @param {String} title - title that will be used to name the Litmus test
*
* @returns {Object} a promise
* 
*/

Litmus.prototype.run = function(html, title) {
  this.title = this.options.subject;
  this.delay = this.options.delay || 3500;

  if( (this.title === undefined) || (this.title.trim().length === 0) ){
    this.title = title;
  }

  this.html = html;
  
  return this.api.getTests()
    .bind(this)
    .then(this.getId)
    .then(this.getVersionResults)
    .then(this.removeClients)
    .then(this.sendTest)
    .catch(function(err){ this.logErr(err); })
    .return(html);

};


// LOGGING HELPERS

Litmus.prototype.log = function(str) {
  return console.log(chalk.cyan(str));
};


Litmus.prototype.logSuccess = function(str) {
  return console.log(chalk.green(str));
};


Litmus.prototype.logWarn = function(str) {
  return console.log(chalk.yellow(str));
};

Litmus.prototype.logErr = function(str) {
  return console.log(chalk.red(str));
};

module.exports = Litmus;

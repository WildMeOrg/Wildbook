# Email Templates

This folder contains Wildbook email templates. They are used by the
*org.ecocean.NotificationMailer* class, which fills them with data appropriate
to the function being performed.

Emails can be sent either as plain text only, or as MIME-multipart plain/HTML.
If only a plain text template exists for a given template type, then only a
plain text email will be sent. If both ```.txt``` and ```.html``` template files
exist, then the default is to send a MIME-multipart email, although this can be
prevented in the *NotificationMailer* instance used. At least the plain text
template must exist. Subject lines are specified in the first line of the plain
text template, with the prefix **SUBJECT:** to highlight the fact.

The base templates for all emails are:

* ```email-template.txt```
* ```email-template.html```

The *NotificationMailer* class also allows to specify a template **type**, which
might be, for example, "newSubmission". This would load these templates:

* ```newSubmission.txt```
* ```newSubmission.html```

then use the text in each of those to replace the **@EMAIL_CONTENT@** tag in the
respective base templates. Once these final text/HTML templates have been
collated (during initialization of *NotificationMailer*) the tag-map specified
at creation time is used to perform tag search/replacement in the templates.

When creating an instance of the *NotificationMailer* class to send an email, it
requires a tag-map comprising tags to be replaced with text values. Tags are
generally specified in the templates with delimiting @ characters to isolate
them from other text.

The *NotificationMailer* also supports being created without a tag-map, but just
a single text string instead. In this case it is used to replace a standard
**@TEXT_CONTENT@** tag in the templates.

USAGE
=====

jQuery.ptTimeSelect is called agaist input fields to attach a
Time Select widget to each matched element. Each element, when
focused upon, will display a time selection popoup where the
user can define a time.


EXAMPLE
-------

    <input name="time" value="" />
    <script type="text/javascript">
        $(document).ready(function(){
            $('input[name="time"]').ptTimeSelect();
        });
    </script>

INPUT PARAMETERS
----------------

The plugin accepts only one input parameter; a javascript object
with the list of options. All options are optional. See below
for a list of supported options

    $('input[name="time"]').ptTimeSelect({
        hoursLabel: 'HRS'
    });

OPTIONS
-------


+   *containerClass*    - A class to be associated with the popup widget. Default is undefined
+   *containerWidth*    - Css width for the container. Default is "20em".
+   *hoursLabel*        - Label for the Hours. Default is "Hours"
+   *minutesLabel*      - Label for the Mintues container. Default is "Minutes"
+   *setButtonLabel*    - Label for the Set button. Deafult is "Set"
+   *popupImage*        - The html element (ex. img or text) to be appended next
                          to each input field and that will display the time
                          select widget upon click. Default is blank ""
+   *zIndex*            - Integer for the popup widget z-index. Default is 10
+   *onBeforeShow*      - Function to be called before the widget is made visible
                          to the user. Function is passed 2 arguments: 1) the 
                          input field as a jquery object and 2) the popup widget
                          as a jquery object. Default is undefined
+   *onClose*           - Function to be called after closing the popup widget.
                          Function is passed 1 argument: the input field as a 
                          jquery object. Default is undefined
+   *onFocusDisplay*    - True or False indicating if popup is auto displayed
                          upon focus of the input field. Default is true
    

### Example

    $('input[name="time"]')
        .ptTimeSelect({
            containerClass: undefined,
            containerWidth: undefined,
            hoursLabel:     'Hour',
            minutesLabel:   'Minutes',
            setButtonLabel: 'Set',
            popupImage:     undefined,
            onFocusDisplay: true,
            zIndex:         10,
            onBeforeShow:   undefined,
            onClose:        undefined
        });


RETURNS
-------

Function will return a jQuery object with the input selection,
thus maintaining jQuery's chainable nature.

    <input name="time" value="" style="display:none;" />
    <script type="text/javascript">
        $(document).ready(function(){
            $('input[name="time"]')
                .ptTimeSelect({
                    zIndex: 100,
                    onBeforeShow: function(input, widget){
                        // do something before the widget is made visible.
                    }
                })
                .show();
        });
    </script>


